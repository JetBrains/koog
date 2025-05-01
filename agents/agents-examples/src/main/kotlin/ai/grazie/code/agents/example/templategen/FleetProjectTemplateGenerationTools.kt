package ai.grazie.code.agents.example.templategen

import ai.grazie.code.agents.core.tools.*
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
