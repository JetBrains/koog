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
     * - File metadata (path, name, extension, size, content type)
     * - Content as either full text or line-range excerpt
     * - File attributes (hidden status, file type)
     *
     * @property file the file entry containing metadata and content
     * @constructor creates a new Result instance with the specified file entry
     */
    @Serializable
    public data class Result(val file: FileSystemEntry.File) : ToolResult.JSONSerializable<Result> {
        /**
         * Returns the Kotlin serialization serializer for this result type.
         *
         * @return serializer instance for Result
         */
        override fun getSerializer(): KSerializer<Result> = serializer()

        /**
         * Converts the result to a structured text representation.
         *
         * Renders the file information in the following format:
         * - File path with metadata in parentheses (content type, size, visibility)
         * - Content section with either:
         *     - Full text in a code block for complete file reads
         *     - Excerpt with line ranges and code blocks for partial reads
         *     - No content section if content is [FileSystemEntry.File.Content.None]
         *
         * @return formatted text representation of the file
         */
        override fun toStringDefault(): String = text { file(file) }
    }

    override val argsSerializer: KSerializer<Args> = Args.serializer()
    override val descriptor: ToolDescriptor = Companion.descriptor

    /**
     * Executes the file reading operation with the specified arguments.
     *
     * Performs validation before reading:
     * - Verifies the path exists in the filesystem
     * - Confirms the path points to a file (not a directory)
     * - Ensures the file can be successfully read
     *
     * @param args arguments specifying the file path and optional line range
     * @return Result containing the file with its content and metadata
     * @throws [ToolException.ValidationFailure] if the file doesn't exist, is a directory, or
     *   cannot be read
     * @throws IllegalArgumentException if line range parameters are invalid
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
         * Tool descriptor defining name, description, and parameters.
         *
         * Configures the tool as "read-file" with absolute path requirement and optional line range
         * parameters using 0-based indexing.
         *
         * Required parameters:
         * - `path`: Absolute path to the target file
         *
         * Optional parameters:
         * - `startLine`: First line to include, 0-based, defaults to 0
         * - `endLine`: First line to exclude, 0-based, -1 for the end of file, defaults to -1
         */
        public val descriptor: ToolDescriptor = ToolDescriptor(
            name = "read-file",
            description = text {
                +"Reads text file with optional line range selection."
                +"Returns formatted content with metadata."
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
