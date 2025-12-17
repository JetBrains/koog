package ai.koog.agents.ext.tool.file

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolException
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.validate
import ai.koog.agents.core.tools.validateNotNull
import ai.koog.agents.ext.tool.file.filter.GlobPattern
import ai.koog.agents.ext.tool.file.model.FileSystemEntry
import ai.koog.agents.ext.tool.file.render.folder
import ai.koog.prompt.text.text
import ai.koog.rag.base.files.FileMetadata
import ai.koog.rag.base.files.FileSystemProvider
import kotlinx.serialization.Serializable

/**
 * Provides functionality to list directory contents with configurable depth and glob filtering parameters,
 * returning a structured directory tree with file and folder metadata.
 *
 * @param Path the filesystem path type used by the provider
 * @property fs read-only filesystem provider for accessing directories
 */
public class ListDirectoryTool<Path>(private val fs: FileSystemProvider.ReadOnly<Path>) :
    Tool<ListDirectoryTool.Args, ListDirectoryTool.Result>(
        argsSerializer = Args.serializer(),
        resultSerializer = Result.serializer(),
        name = "__list_directory__",
        description = """
            Lists directory contents with optional pattern-based file search. READ-ONLY.
            Use to:
            - Explore: see what files/folders exist
            - Search: find files by pattern (use filter + higher depth)
            Returns a tree with file paths, sizes, and line counts.
        """.trimIndent()
    ) {

    /**
     * Specifies which directory to list and how to traverse its contents.
     *
     * @property path absolute filesystem path to the target directory
     * @property depth how many levels deep to traverse (1 = direct children only, 2 = include subdirectories, etc.), defaults to 1
     * @property filter glob pattern to match specific files/folders (e.g., "*.kt" for Kotlin files), defaults to null
     */
    @Serializable
    public data class Args(
        @property:LLMDescription("Absolute path to the directory to list (e.g. /home/user/project). Don't use relative path like '.'")
        val path: String,
        @property:LLMDescription("Directory levels to traverse. 1 = immediate contents only (default), 2 = include subdirectories, etc. When searching with ** glob patterns, use 5-10 to reach deeply nested files.")
        val depth: Int = 1,
        @property:LLMDescription("""
            Glob pattern for finding files (case-insensitive). Output shows matching files with their paths; paths without matches are omitted.
            Pattern sees each file's path from the listed directory, so 'src/**/*.kt' finds Kotlin files under src/.
            Glob: * (in segment), ** (across segments, limited by depth), ?, {a,b}.
            """)
        val filter: String? = null
    )

    /**
     * Contains the successfully listed directory with its hierarchical structure and metadata.
     *
     * The result encapsulates a [FileSystemEntry.Folder] which includes:
     * - Directory metadata (name, path, hidden status)
     * - Child entries organized hierarchically with their metadata
     *
     * @property root the directory tree starting from the requested path
     */
    @Serializable
    public data class Result(val root: FileSystemEntry.Folder)

    /**
     * Lists directory contents from the filesystem with optional depth and pattern filtering.
     *
     * Performs validation before listing:
     * - Validates the depth parameter is positive
     * - Verifies the path exists in the filesystem
     * - Confirms the path points to a directory
     *
     * @param args arguments specifying the directory path, depth, and optional filter
     * @return [Result] containing the directory tree with its contents and metadata
     * @throws ToolException.ValidationFailure if the path doesn't exist, isn't a directory,
     *         depth is invalid, or filter matches nothing
     */
    override suspend fun execute(args: Args): Result {
        validate(args.depth > 0) { "Depth must be at least 1 (got ${args.depth})" }

        val path = fs.fromAbsolutePathString(args.path)
        val metadata = validateNotNull(fs.metadata(path)) { "Path does not exist: ${args.path}" }

        validate(metadata.type == FileMetadata.FileType.Directory) {
            "Path is not a directory: ${args.path} (it's a ${metadata.type})"
        }

        val entry = buildDirectoryTree(
            fs = fs,
            start = path,
            startMetadata = metadata,
            maxDepth = args.depth,
            filter = args.filter?.ifEmpty { null }?.let {
                GlobPattern(pattern = it, caseSensitive = false)
            }
        )

        validate(entry != null) {
            "No files or directories match the pattern '${args.filter}' in ${args.path}"
        }

        return Result(entry as FileSystemEntry.Folder)
    }

    override fun encodeResultToString(result: Result): String = with(result) {
        text { folder(root) }
    }
}
