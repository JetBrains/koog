package ai.koog.agents.ext.tool.file

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolException
import ai.koog.agents.core.tools.ToolResult
import ai.koog.agents.core.tools.ToolResultUtils
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.validate
import ai.koog.agents.core.tools.validateNotNull
import ai.koog.agents.ext.tool.file.filter.GlobPattern
import ai.koog.agents.ext.tool.file.model.FileSystemEntry
import ai.koog.agents.ext.tool.file.render.folder
import ai.koog.prompt.text.text
import ai.koog.rag.base.files.FileMetadata
import ai.koog.rag.base.files.FileSystemProvider
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Provides functionality to list directory contents with configurable depth and glob filtering parameters,
 * returning a structured directory tree with file and folder metadata.
 *
 * @param Path the filesystem path type used by the provider
 * @property fs read-only filesystem provider for accessing directories
 */
public class ListDirectoryTool<Path>(private val fs: FileSystemProvider.ReadOnly<Path>) :
    Tool<ListDirectoryTool.Args, ListDirectoryTool.Result>() {

    /**
     * Specifies which directory to list and how to traverse its contents.
     *
     * @property path absolute filesystem path to the target directory
     * @property depth how many levels deep to traverse (1 = direct children only, 2 = include subdirectories, etc.), defaults to 1
     * @property filter glob pattern to match specific files/folders (e.g., "*.kt" for Kotlin files), defaults to null
     */
    @Serializable
    public data class Args(
        @property:LLMDescription("Absolute path to the directory you want to list (e.g., /home/user/project)")
        val path: String,
        @property:LLMDescription("How many levels deep to go. 1 = only direct contents, 2 = include subdirectories, etc. Default is 1")
        val depth: Int = 1,
        @property:LLMDescription("Glob pattern to match files/folders. Examples: '*.txt' for text files, '**/*.kt' for all Kotlin files at any depth")
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
    public data class Result(val root: FileSystemEntry.Folder) : ToolResult.TextSerializable() {
        /**
         * Converts the result to a structured text representation.
         *
         * Renders the directory tree in the following format:
         * - Directory paths end with `/` and increase indent by 2 spaces per level
         * - File paths with metadata in parentheses (size, line count if available, "hidden" if the file is hidden)
         * - Filtered results show only matching entries
         *
         * Example:
         * ```
         * /project/
         *   src/
         *     Main.kt (1.5 KiB, 42 lines)
         *     Utils.kt (0.8 KiB, 28 lines)
         *   README.md (2.1 KiB, 67 lines)
         *   .gitignore (0.1 KiB, 12 lines, hidden)
         * ```
         *
         * @return formatted text representation of the directory tree
         */
        override fun textForLLM(): String = text { folder(root) }
    }

    override val argsSerializer: KSerializer<Args> = Args.serializer()
    override val resultSerializer: KSerializer<Result> = ToolResultUtils.toTextSerializer()
    override val name: String = "__list_directory__"
    override val description: String = """
        Lists files and subdirectories in a directory. READ-ONLY - never modifies anything.
        
        Use this to:
        - See what files exist before reading or creating
        - Understand project structure
        - Find specific files with patterns
        
        Returns a tree showing all contents with sizes and metadata.
    """.trimIndent()

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
            filter = args.filter?.let { GlobPattern(it, caseSensitive = false) }
        )

        validate(entry != null) {
            "No files or directories match the pattern '${args.filter}' in ${args.path}"
        }

        return Result(entry as FileSystemEntry.Folder)
    }
}
