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
import ai.koog.agents.file.tools.model.FileSystemEntry
import ai.koog.agents.file.tools.render.file
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
        val path: String,
        val startLine: Int = 0,
        val endLine: Int = -1,
    ) : ToolArgs

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
    public data class Result(val file: FileSystemEntry.File) : ToolResult.JSONSerializable<Result> {
        override fun getSerializer(): KSerializer<Result> = serializer()

        /**
         * Converts the result to a structured text representation.
         *
         * Renders the file information in the following format:
         * - File path with metadata in parentheses (size, line count if available, "hidden" if the file is hidden)
         * - Content section with either:
         *     - Full text for complete file reads
         *     - Excerpt with line ranges for partial reads
         *     - No content section if content is [FileSystemEntry.File.Content.None]
         *
         * @return formatted text representation of the file
         */
        override fun toStringDefault(): String = text { file(file) }
    }

    override val argsSerializer: KSerializer<Args> = Args.serializer()
    override val descriptor: ToolDescriptor = Companion.descriptor

    /**
     * Reads file content from the filesystem with optional line range filtering.
     *
     * Performs validation before reading:
     * - Verifies the path exists in the filesystem
     * - Confirms the path points to a file
     * - Confirms the file is a text file
     *
     * @param args arguments specifying the file path and optional line range
     * @return [Result] containing the file with its content and metadata
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
            Result(
                buildTextFileEntry(
                    fs = fs,
                    path = path,
                    metadata = metadata,
                    startLine = args.startLine,
                    endLine = args.endLine,
                )
            )
        }.onFailure { e ->
            if (e is IllegalArgumentException) {
                throw ToolException.ValidationFailure(
                    e.message ?: "Invalid line range: startLine=${args.startLine}, endLine=${args.endLine}"
                )
            }
        }.getOrThrow()
    }

    public companion object {
        /**
         * Provides a tool descriptor for the read file operation.
         *
         * Configures the tool to read text files with optional line range selection
         * using 0-based indexing.
         */
        public val descriptor: ToolDescriptor = ToolDescriptor(
            name = "__read_file__",
            description = """
                Reads a text file (throws if non-text) with optional line range selection. TEXT-ONLY - never reads binary files.
                
                Use this to:
                - Read entire text files or specific line ranges
                - Get file content along with metadata
                - Extract portions of files using 0-based line indexing
                
                Returns file content and metadata (name, extension, path, hidden, size, contentType).
            """.trimIndent(),
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "path",
                    description = "Absolute path to the text file you want to read (e.g., /home/user/file.txt)",
                    type = ToolParameterType.String
                )
            ),
            optionalParameters = listOf(
                ToolParameterDescriptor(
                    name = "startLine",
                    description = "First line to include (0-based, inclusive). Default is 0 to start from beginning",
                    type = ToolParameterType.Integer
                ),
                ToolParameterDescriptor(
                    name = "endLine",
                    description = "First line to exclude (0-based, exclusive). Use -1 to read until end. Default is -1",
                    type = ToolParameterType.Integer
                )
            )
        )
    }
}
