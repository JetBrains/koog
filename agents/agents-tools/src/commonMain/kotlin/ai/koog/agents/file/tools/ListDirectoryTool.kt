package ai.koog.agents.file.tools

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolException
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.ToolResult
import ai.koog.agents.core.tools.validate
import ai.koog.agents.core.tools.validateNotNull
import ai.koog.agents.file.tools.filter.GlobPattern
import ai.koog.agents.file.tools.model.FileSystemEntry
import ai.koog.agents.file.tools.render.folder
import ai.koog.prompt.text.text
import ai.koog.rag.base.files.FileMetadata
import ai.koog.rag.base.files.FileSystemProvider
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Tool that lists directory contents as a hierarchical tree.
 *
 * Reads directory structure without modifying anything. Supports depth control
 * and glob pattern filtering to focus on specific files.
 *
 * @param Path the filesystem path type
 * @property fs filesystem provider for read-only directory access
 */
public class ListDirectoryTool<Path>(private val fs: FileSystemProvider.ReadOnly<Path>) :
    Tool<ListDirectoryTool.Args, ListDirectoryTool.Result>() {

    /**
     * Parameters for listing a directory.
     *
     * @property path absolute path to the directory to list
     * @property depth how many levels deep to traverse (1 = direct children only, 2 = include subdirectories, etc.)
     * @property filter glob pattern to match specific files/folders (e.g., "*.kt" for Kotlin files)
     */
    @Serializable
    public data class Args(
        val path: String,
        val depth: Int = 1,
        val filter: String? = null
    ) : ToolArgs

    /**
     * The directory listing result containing a tree of files and folders.
     *
     * Contains a [FileSystemEntry.Folder] representing the listed directory
     * with all its contents organized hierarchically.
     *
     * @property root the directory tree starting from the requested path
     */
    @Serializable
    public data class Result(val root: FileSystemEntry.Folder) : ToolResult.JSONSerializable<Result> {
        override fun getSerializer(): KSerializer<Result> = serializer()

        /**
         * Formats the tree as indented text for display.
         *
         * Shows files with size/line counts and marks hidden files.
         * Directories end with `/` and indent increases by 2 spaces per level.
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
         */
        override fun toStringDefault(): String = text { folder(root) }
    }

    override val argsSerializer: KSerializer<Args> = Args.serializer()
    override val descriptor: ToolDescriptor = Companion.descriptor

    /**
     * Lists the directory and returns its contents as a tree.
     *
     * @param args the directory path, depth, and optional filter
     * @return tree structure of the directory contents
     * @throws ToolException.ValidationFailure if a path doesn't exist, isn't a directory,
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
            filter = args.filter?.let { GlobPattern.compile(it, caseSensitive = false) }
        )

        validate(entry != null) {
            "No files or directories match the pattern '${args.filter}' in ${args.path}"
        }

        return Result(entry as FileSystemEntry.Folder)
    }

    public companion object {
        public val descriptor: ToolDescriptor = ToolDescriptor(
            name = "__list_directory__",
            description = """
                Lists files and subdirectories in a directory. READ-ONLY - never modifies anything.
                
                Use this to:
                - See what files exist before reading or creating
                - Understand project structure
                - Find specific files with patterns
                
                Returns a tree showing all contents with sizes and metadata.
            """.trimIndent(),
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "path",
                    description = "Absolute path to the directory you want to list (e.g., /home/user/project)",
                    type = ToolParameterType.String
                )
            ),
            optionalParameters = listOf(
                ToolParameterDescriptor(
                    name = "depth",
                    description = "How many levels deep to go. 1 = only direct contents, 2 = include subdirectories, etc. Default is 1",
                    type = ToolParameterType.Integer
                ),
                ToolParameterDescriptor(
                    name = "filter",
                    description = "Glob pattern to match files/folders. Examples: '*.txt' for text files, '**/*.kt' for all Kotlin files at any depth",
                    type = ToolParameterType.String
                )
            )
        )
    }
}
