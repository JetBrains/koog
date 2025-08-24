package ai.koog.agents.file.tools

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolException
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.ToolResult
import ai.koog.agents.core.tools.fail
import ai.koog.agents.core.tools.validate
import ai.koog.agents.file.tools.model.FileSystemEntry
import ai.koog.agents.file.tools.render.file
import ai.koog.prompt.text.text
import ai.koog.rag.base.files.FileMetadata
import ai.koog.rag.base.files.FileSystemProvider
import ai.koog.rag.base.files.readText
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
     * - Content as either full text or line-range excerpt
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
     * - Confirms the path points to a file (not a directory)
     *
     * @param args arguments specifying the file path and optional line range
     * @return [Result] containing the file with its content and metadata
     * @throws [ToolException.ValidationFailure] if the file doesn't exist, is a directory, or
     *   cannot be read
     * @throws [IllegalArgumentException] if line range parameters are invalid
     */
    override suspend fun execute(args: Args): Result {
        val path = fs.fromAbsolutePathString(args.path)

        validate(fs.exists(path)) { "File does not exist: ${args.path}" }
        validate(fs.metadata(path)?.type == FileMetadata.FileType.File) {
            "Path must point to a file, not a directory: ${args.path}"
        }

        val file = FileSystemEntry.File.of(
            path,
            content = FileSystemEntry.File.Content.of(
                fs.readText(path),
                args.startLine,
                args.endLine,
            ),
            fs = fs,
        ) ?: fail("Unable to read file: ${args.path}")

        return Result(file)
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
            description = text {
                +"Reads text file content with optional line range selection."
                +"Returns file content along with metadata (path, size, line count, hidden status)."
                newline()
                +"Uses 0-based line indexing."
            },
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "path",
                    description = text { +"Absolute path to target file." },
                    type = ToolParameterType.String,
                )
            ),
            optionalParameters = listOf(
                ToolParameterDescriptor(
                    name = "startLine",
                    description = text { +"First line to include (0-based, inclusive)." },
                    type = ToolParameterType.Integer,
                ),
                ToolParameterDescriptor(
                    name = "endLine",
                    description = text {
                        +"First line to exclude (0-based, exclusive). Use -1 for end."
                    },
                    type = ToolParameterType.Integer,
                ),
            ),
        )
    }
}
