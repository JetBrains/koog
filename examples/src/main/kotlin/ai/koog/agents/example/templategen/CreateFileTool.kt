package ai.koog.agents.example.templategen

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import kotlinx.serialization.Serializable

/**
 * Tool for creating a file at the specified path with the given content.
 * This is used by the FleetProjectGeneration agent to create files in the project template.
 */
object CreateFileTool : SimpleTool<CreateFileTool.Args>() {
    const val PATH_PARAMETER = "path"
    const val CONTENT_PARAMETER = "content"

    @Serializable
    data class Args(
        val path: String,
        val content: String?,
    ) : Tool.Args

    override val argsSerializer = Args.serializer()

    override val descriptor = ToolDescriptor(
        name = "create_file",
        description = "Creates a new file at the specified path with the given content",
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = PATH_PARAMETER,
                description = "Path where to create the file",
                type = ToolParameterType.String
            )
        ),
        optionalParameters = listOf(
            ToolParameterDescriptor(
                name = CONTENT_PARAMETER,
                description = "Content to write to the file",
                type = ToolParameterType.String
            )
        )
    )

    override suspend fun doExecute(args: Args): String {
        // In a real implementation, this would create a file with content
        // For now, we just log the operation
        println("Created file at ${args.path} with content length ${args.content?.length}")
        return "Created file at ${args.path} with content length ${args.content?.length}"
    }
}
