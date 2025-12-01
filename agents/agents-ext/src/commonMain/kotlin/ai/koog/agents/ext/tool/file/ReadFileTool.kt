package ai.koog.agents.ext.tool.file

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolException
import ai.koog.agents.core.tools.ToolResult
import ai.koog.agents.core.tools.ToolResultUtils
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.validate
import ai.koog.agents.core.tools.validateNotNull
import ai.koog.agents.ext.tool.file.model.FileSystemEntry
import ai.koog.agents.ext.tool.file.render.file
import ai.koog.prompt.text.text
import ai.koog.rag.base.files.FileMetadata
import ai.koog.rag.base.files.FileSystemProvider
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Provides functionality to read file contents with configurable start and end line parameters,
 * returning structured file metadata and content.
 *
 * @param Path the filesystem path type used by the provider
 * @property fs read-only filesystem provider for accessing files
 */
public class ReadFileTool<Path>(private val fs: FileSystemProvider.ReadOnly<Path>) :
    Tool<ReadFileTool.Args, ReadFileTool.Result>() {

    /**
     * Specifies which file to read and what portion of its content to extract.
     *
     * @property path absolute filesystem path to the target file
     * @property startLine the first line to include (0-based, inclusive), defaults to 0
     * @property endLine the first line to exclude (0-based, exclusive), -1 means read to end,
     *   defaults to -1
     */
    @Serializable
    public data class Args(
        @property:LLMDescription("Absolute path to the text file you want to read (e.g., /home/user/file.txt)")
        val path: String,
        @property:LLMDescription("First line to include (0-based, inclusive). Default is 0 to start from beginning")
        val startLine: Int = 0,
        @property:LLMDescription("First line to exclude (0-based, exclusive). Use -1 to read until end. Default is -1")
        val endLine: Int = -1,
    )

    /**
     * Contains the successfully read file with its metadata and extracted content.
     *
     * The result encapsulates a [FileSystemEntry.File] which includes:
     * - File metadata (path, name, extension, size, content type, hidden status)
     * - Content as either full-text or line-range excerpt
     *
     * @property file the file entry containing metadata and content
     */
    @Serializable
    public data class Result(
        val file: FileSystemEntry.File,
        internal val warningMessage: String? = null
    ) : ToolResult.TextSerializable() {
        /**
         * Converts the result to a structured text representation.
         *
         * Renders the file information in the following format:
         * - Warning message (if present)
         * - File path with metadata in parentheses (size, line count if available, "hidden" if the file is hidden)
         * - Content section with either:
         *     - Full text for complete file reads
         *     - Excerpt with line ranges for partial reads
         *     - No content section if content is [FileSystemEntry.File.Content.None]
         *
         * @return formatted text representation of the file
         */
        override fun textForLLM(): String = text {
            warningMessage?.let {
                +"Warning: $it"
                +""
            }
            file(file)
        }
    }

    override val argsSerializer: KSerializer<Args> = Args.serializer()
    override val resultSerializer: KSerializer<Result> = ToolResultUtils.toTextSerializer()
    override val name: String = "__read_file__"
    override val description: String = """
        Reads a text file (throws if non-text) with optional line range selection. TEXT-ONLY - never reads binary files.
        
        Use this to:
        - Read entire text files or specific line ranges
        - Get file content along with metadata
        - Extract portions of files using 0-based line indexing
        
        Returns file content and metadata (name, extension, path, hidden, size, contentType).
    """.trimIndent()

    /**
     * Reads file content from the filesystem with optional line range filtering.
     *
     * Performs validation before reading:
     * - Verifies the path exists in the filesystem
     * - Confirms the path points to a file
     * - Confirms the file is a text file
     *
     * If the requested `endLine` exceeds the file's line count, it will be clamped to the available lines
     * and a warning will be included in the result.
     *
     * @param args arguments specifying the file path and optional line range
     * @return [Result] containing the file with its content, metadata, and optional warning
     * @throws [ToolException.ValidationFailure] if the file doesn't exist, is a directory, or is not a text file, or
     *          if line range parameters are invalid
     */
    override suspend fun execute(args: Args): Result {
        val path = fs.fromAbsolutePathString(args.path)
        val metadata =
            validateNotNull(fs.metadata(path)) { "File not found: ${args.path} (ensure the path is absolute)" }
        validate(metadata.type == FileMetadata.FileType.File) { "Not a file: ${args.path}" }

        val type = fs.getFileContentType(path)
        validate(type == FileMetadata.FileContentType.Text) { "File is not a text file: ${args.path}" }

        return runCatching {
            var warningMessage: String? = null

            Result(
                buildTextFileEntry(
                    fs = fs,
                    path = path,
                    metadata = metadata,
                    startLine = args.startLine,
                    endLine = args.endLine,
                    onEndLineExceedsFileLength = { endLine, fileLineCount ->
                        warningMessage = "endLine=$endLine exceeds file length ($fileLineCount lines). " +
                            "Clamped to available lines ${args.startLine}-$fileLineCount."
                    }
                ),
                warningMessage
            )
        }.onFailure { e ->
            if (e is IllegalArgumentException) {
                throw ToolException.ValidationFailure(
                    e.message ?: "Invalid line range: startLine=${args.startLine}, endLine=${args.endLine}"
                )
            }
        }.getOrThrow()
    }
}
