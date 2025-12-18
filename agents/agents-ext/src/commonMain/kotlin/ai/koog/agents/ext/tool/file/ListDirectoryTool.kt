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
            List a directory as a tree so an agent can *orient itself* in an unknown filesystem/repo and decide what to read next.

            This is usually the first tool to call:
            - Find where code, configs, and docs live
            - Confirm filenames and exact paths before reading/editing
            - Narrow the search space with a glob instead of dumping huge directory listings

            Recommended agent workflow:
            1) Call with a small `depth` (often `1` or `2`) to understand the top-level layout.
            2) If you need to locate specific files, add a `filter` (glob) and increase `depth` to '5' or more.
            3) Once you see the exact path(s), switch to other tools to work with content.

            This tool does NOT:
            - Return file contents
            - Search inside files (it only matches on paths via `filter`)
            - Modify the filesystem (read-only)

            Common pitfalls:
            - `filter="*.js"` only matches files directly under `absolutePath`. For “any depth”, use `filter="**/*.js"`.
            - Glob does not override `depth`. If files exist deeper than the traversal can reach, you’ll get “no matches”.

            Returns a structured tree rooted at the requested directory.
        """.trimIndent()
    ) {

    /**
     * Specifies which directory to list and how to traverse its contents.
     *
     * @property absolutePath absolute filesystem path to the target directory
     * @property depth how many levels deep to traverse (1 = direct children only, 2 = include subdirectories, etc.),
     *   defaults to 1; single-child directory chains may be collapsed without consuming depth
     * @property filter optional glob pattern (case-insensitive) matched against normalized relative paths (from
     *   [absolutePath]) using `/` as a separator; defaults to null (no filtering)
     */
    @Serializable
    public data class Args(
        @property:LLMDescription(
            """
            Absolute path to the directory to list.
            Requirements:
            - Must be an absolute path (not relative)
            - Must point to a directory (not a file)
            """
        )
        val absolutePath: String,
        @property:LLMDescription(
            """
            Maximum traversal depth (> 0). Default is `1`.
            Guidance:
            - Start with `1` to avoid large outputs.
            - Increase when you need to see inside subfolders, but prefer adding a `filter` to keep results small.
            """
        )
        val depth: Int = 1,
        @property:LLMDescription(
            """
            Optional glob filter for narrowing results (case-insensitive). Use `null` or `""` to disable filtering.

            What it matches:
            - The pattern is matched against each entry’s *relative path* from `absolutePath` (normalized to `/`, even on Windows).
              Example relative paths: `README.md`, `src/main/kotlin/App.kt`, `tests/__init__.py`.

            What you get back:
            - Matching files are included.
            - Directories are included when they contain matching entries (to preserve structure).
            - If `depth` is too small to reach matches, you may get a “no matches” error even if the files exist deeper.

            Supported syntax:
            - `*` matches within a single path segment (does not cross `/`)
            - `**` can cross `/` (any depth)
            - `?`, `[...]`, `[!...]`, `{a,b}` alternatives are supported

            Practical examples:
            - `"**/*.java"`: all Java files anywhere under `absolutePath`
            - `"*/*.ts"`: TypeScript files exactly 1 folder below `absolutePath`
            - `"*/Test*"`: test files like `test/TestMain.cs`
            - `"**/{build.gradle.kts,settings.gradle.kts}"`: find Gradle build entrypoints
            """
        )
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

        val path = fs.fromAbsolutePathString(args.absolutePath)
        val metadata = validateNotNull(fs.metadata(path)) { "Path does not exist: ${args.absolutePath}" }

        validate(metadata.type == FileMetadata.FileType.Directory) {
            "Path is not a directory: ${args.absolutePath} (it's a ${metadata.type})"
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
            "No files or directories match the pattern '${args.filter}' in ${args.absolutePath}"
        }

        return Result(entry as FileSystemEntry.Folder)
    }

    override fun encodeResultToString(result: Result): String = with(result) {
        text { folder(root) }
    }
}
