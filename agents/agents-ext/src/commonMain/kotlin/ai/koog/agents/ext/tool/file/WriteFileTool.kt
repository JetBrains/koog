package ai.koog.agents.ext.tool.file

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolException
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.validate
import ai.koog.agents.core.tools.validateNotNull
import ai.koog.agents.ext.tool.file.render.entry
import ai.koog.prompt.text.text
import ai.koog.rag.base.files.FileMetadata
import ai.koog.rag.base.files.FileSystemProvider
import ai.koog.rag.base.files.model.FileSystemEntry
import ai.koog.rag.base.files.model.buildFileSystemEntry
import ai.koog.rag.base.files.writeText
import kotlinx.serialization.Serializable

/**
 * Provides functionality to write text content to files at absolute paths,
 * creating parent directories and overwriting existing content as needed.
 *
 * @param Path the filesystem path type used by the provider
 * @property fs read/write filesystem provider for accessing and modifying files
 */
public class WriteFileTool<Path>(private val fs: FileSystemProvider.ReadWrite<Path>) :
    Tool<WriteFileTool.Args, WriteFileTool.Result>(
        argsSerializer = Args.serializer(),
        resultSerializer = Result.serializer(),
        name = "__write_file__",
        description = """
            Writes text content to a file at an absolute path. Creates parent directories if needed and overwrites existing content.

            Use this to:
            - Create new text files with content
            - Replace entire content of existing files

            Returns file metadata (name, extension, path, hidden, size, contentType).
        """.trimIndent()
    ) {

    /**
     * Specifies which file to write and what text content to put into it.
     *
     * @property path absolute filesystem path to the target file
     * @property content text content to write to the file (must not be empty)
     */
    @Serializable
    public data class Args(
        @property:LLMDescription("Absolute path to the target file (e.g., /home/user/file.txt)")
        val path: String,
        @property:LLMDescription("Text content to write (must not be empty). Overwrites existing content completely")
        val content: String
    )

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
    public data class Result(val file: FileSystemEntry.File)

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

    override fun encodeResultToString(result: Result): String = with(result) {
        text {
            +"Written"
            entry(file)
        }
    }
}
