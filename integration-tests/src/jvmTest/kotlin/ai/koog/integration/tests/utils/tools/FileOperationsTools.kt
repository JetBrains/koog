package ai.koog.integration.tests.utils.tools

import ai.koog.agents.core.tools.SimpleTool
import kotlinx.serialization.Serializable

class FileOperationsTools {
    val fileContentsByPath = mutableMapOf<String, String>()

    val createNewFileWithTextTool = CreateNewFileWithText(this)
    val readFileContentTool = ReadFileContent(this)

    class CreateNewFileWithText(private val fileOperationsTools: FileOperationsTools) :
        SimpleTool<CreateNewFileWithText.Args>() {
        @Serializable
        data class Args(
            val pathInProject: String,
            val text: String
        )

        override val argsSerializer = Args.serializer()
        override val description = "Creates a new file at the specified path with the provided text content"

        override suspend fun doExecute(args: Args): String {
            return fileOperationsTools.createNewFileWithText(args.pathInProject, args.text)
        }
    }

    class ReadFileContent(private val fileOperationsTools: FileOperationsTools) : SimpleTool<ReadFileContent.Args>() {
        @Serializable
        data class Args(
            val pathInProject: String
        )

        override val argsSerializer = Args.serializer()

        override val description = "Reads the content of a file at the specified path"

        override suspend fun doExecute(args: Args): String {
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
