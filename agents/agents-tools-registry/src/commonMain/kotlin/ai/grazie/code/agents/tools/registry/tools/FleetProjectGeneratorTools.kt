package ai.grazie.code.agents.tools.registry.tools

import ai.grazie.code.agents.core.tools.*
import kotlinx.serialization.Serializable

object FleetProjectGeneratorTools {
    abstract class CreateDirectoryTool : SimpleTool<CreateDirectoryTool.Args>() {
        @Serializable
        data class Args(val parentDirectory: String, val name: String) : Tool.Args
        
        final override val argsSerializer = Args.serializer()

        final override val descriptor = ToolDescriptor(
            name = "create-directory",
            description = "Creates new directory",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "parentDirectory",
                    description = "Parent directory for the new one",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "name",
                    description = "Name of the new directory",
                    type = ToolParameterType.String
                )
            )
        )
    }

    abstract class CreateFileTool : SimpleTool<CreateFileTool.Args>() {
        @Serializable
        data class Args(val parentDirectory: String, val name: String) : Tool.Args

        final override val argsSerializer = Args.serializer()

        final override val descriptor = ToolDescriptor(
            name = "create-file",
            description = "Creates new file",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "parentDirectory",
                    description = "Parent directory for the new file",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "name",
                    description = "Name of the new file",
                    type = ToolParameterType.String
                )
            )
        )
    }

    abstract class SetFileTextTool : SimpleTool<SetFileTextTool.Args>() {
        @Serializable
        data class Args(val filePath: String, val text: String) : Tool.Args

        final override val argsSerializer = Args.serializer()

        final override val descriptor = ToolDescriptor(
            name = "set-file-text",
            description = "Updates the content of the given file",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "filePath",
                    description = "Path of the given file",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "text",
                    description = "New content for the given file",
                    type = ToolParameterType.String
                )
            ),
            optionalParameters = emptyList()
        )
    }

    abstract class LogTool : SimpleTool<LogTool.Args>() {
        @Serializable
        data class Args(val systemPrompt: String) : Tool.Args

        final override val argsSerializer = Args.serializer()

        final override val descriptor = ToolDescriptor(
            name = "log",
            description = "Writes the log (prompt) to the screen",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "systemPrompt",
                    description = "Prompt to be written in logs",
                    type = ToolParameterType.String
                )
            ),
            optionalParameters = emptyList()
        )
    }
}