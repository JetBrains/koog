package ai.koog.agents.example.templategen

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import kotlinx.serialization.Serializable

/**
 * Tool for creating a directory at the specified path.
 * This is used by the FleetProjectGeneration agent to create directories in the project template.
 */
object CreateFolderTool : SimpleTool<CreateFolderTool.Args>() {
    const val PATH_PARAMETER = "path"

    @Serializable
    data class Args(
        val path: String,
    ) : Tool.Args

    override val argsSerializer = Args.serializer()

    override val descriptor = ToolDescriptor(
        name = "create_folder",
        description = "Creates a new directory at the specified path",
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = PATH_PARAMETER,
                description = "Path where to create the directory",
                type = ToolParameterType.String
            )
        ),
        optionalParameters = emptyList()
    )

    override suspend fun doExecute(args: Args): String {
        // In a real implementation, this would create a directory
        // For now, we just log the operation
        println("Created directory at ${args.path}")
        return "Created directory at ${args.path}"
    }
}
