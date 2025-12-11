package ai.koog.integration.tests.utils.tools

import ai.koog.agents.core.tools.SimpleTool
import kotlinx.serialization.Serializable

class FileOperationsTools {
    val fileContentsByPath = mutableMapOf<String, String>()

    val createNewFileWithTextTool = CreateNewFileWithText(this)
    val readFileContentTool = ReadFileContent(this)

    class CreateNewFileWithText(private val fileOperationsTools: FileOperationsTools) :
        SimpleTool<CreateNewFileWithText.Args>(
            argsSerializer = Args.serializer(),
            name = "create_new_file_with_text",
            description = "Creates a new file at the specified path with the provided text content"
        ) {
        @Serializable
        data class Args(
            val pathInProject: String,
            val text: String
        )

        override suspend fun execute(args: Args): String {
            return fileOperationsTools.createNewFileWithText(args.pathInProject, args.text)
        }
    }

    class ReadFileContent(private val fileOperationsTools: FileOperationsTools) :
        SimpleTool<ReadFileContent.Args>(
            argsSerializer = Args.serializer(),
            name = "read_file_content",
            description = "Reads the content of a file at the specified path"
        ) {
        @Serializable
        data class Args(
            val pathInProject: String
        )

        override suspend fun execute(args: Args): String {
            return fileOperationsTools.readFileContent(args.pathInProject)
        }
    }

    fun createNewFileWithText(pathInProject: String, text: String): String {
        fileContentsByPath[pathInProject] = text
        return "OK"
    }

    fun readFileContent(pathInProject: String): String {
        return fileContentsByPath[pathInProject] ?: "Error: file not found"
    }
}
