package ai.koog.agents.ext.tool.file

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolException
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.ToolResult
import ai.koog.agents.core.tools.validate
import ai.koog.agents.core.tools.validateNotNull
import ai.koog.agents.ext.tool.file.model.FileSystemEntry
import ai.koog.agents.ext.tool.file.model.buildFileSystemEntry
import ai.koog.agents.ext.tool.file.render.entry
import ai.koog.prompt.text.text
import ai.koog.rag.base.files.FileMetadata
import ai.koog.rag.base.files.FileSystemProvider
import ai.koog.rag.base.files.writeText
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Provides functionality to write text content to files at absolute paths,
 * creating parent directories and overwriting existing content as needed.
 *
 * @param Path the filesystem path type used by the provider
 * @property fs read/write filesystem provider for accessing and modifying files
 */
public class WriteFileTool<Path>(private val fs: FileSystemProvider.ReadWrite<Path>) :
    Tool<WriteFileTool.Args, WriteFileTool.Result>() {

    /**
     * Specifies which file to write and what text content to put into it.
     *
     * @property path absolute filesystem path to the target file
     * @property content text content to write to the file (must not be empty)
     */
    @Serializable
    public data class Args(
        val path: String,
        val content: String
    ) : ToolArgs

    /**
     * Contains the successfully written file with its metadata.
     *
     * The result encapsulates a [FileSystemEntry.File] which includes:
     * - File metadata (name, extension, path, hidden, size, contentType)
     * - No content body (this tool writes, it does not return the file text)
     *
     * @property file the written file entry containing metadata
     */
    @Serializable
    public data class Result(val file: FileSystemEntry.File) : ToolResult.JSONSerializable<Result> {
        override fun getSerializer(): KSerializer<Result> = serializer()

        /**
         * Converts the result to a confirmation message with file metadata.
         *
         * Renders the writing confirmation in the following format:
         * - "Written" confirmation message
         * - File path with metadata in parentheses (size, line count if available, "hidden" if the file is hidden)
         *
         * @return formatted text representation after writing the file
         */
        override fun toStringDefault(): String = text {
            +"Written"
            entry(file)
        }
    }

    override val argsSerializer: KSerializer<Args> = Args.serializer()
    override val descriptor: ToolDescriptor = Companion.descriptor

    /**
     * Writes text content to the filesystem at the specified absolute path.
     *
     * Performs validation before writing:
     * - Verifies the content is not empty
     * - Creates parent directories if they don't exist
     * - Overwrites any existing file content
     *
     * @param args arguments specifying the file path and content to write
     * @return [Result] containing the written file with its metadata
     * @throws ToolException.ValidationFailure if content is empty or the target is not a file after writing the content
     */
    override suspend fun execute(args: Args): Result {
        validate(args.content.isNotEmpty()) { "Content must not be empty" }

        val path = fs.fromAbsolutePathString(args.path)
        fs.writeText(path, args.content)

        val metadata = validateNotNull(fs.metadata(path)) {
            "Failed to read metadata after write: ${args.path}"
        }
        validate(metadata.type == FileMetadata.FileType.File) {
            "Target path is not a file after write: ${args.path}"
        }

        val fileEntry = buildFileSystemEntry(fs, path, metadata) as FileSystemEntry.File
        return Result(fileEntry)
    }

    public companion object {
        /**
         * Provides a tool descriptor for the write file operation.
         *
         * Configures the tool to write text files with content validation
         * and automatic parent directory creation.
         */
        public val descriptor: ToolDescriptor = ToolDescriptor(
            name = "__write_file__",
            description = """
            Writes text content to a file at an absolute path. Creates parent directories if needed and overwrites existing content.
            
            Use this to:
            - Create new text files with content
            - Replace entire content of existing files
            
            Returns file metadata (name, extension, path, hidden, size, contentType).
            """.trimIndent(),
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "path",
                    description = "Absolute path to the target file (e.g., /home/user/file.txt)",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "content",
                    description = "Text content to write (must not be empty). Overwrites existing content completely",
                    type = ToolParameterType.String
                )
            )
        )
    }
}
