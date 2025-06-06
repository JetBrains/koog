package ai.koog.agents.testing.feature

import ai.koog.agents.core.tools.*
import kotlinx.serialization.Serializable

object DummyTool : SimpleTool<ToolArgs.Empty>() {
    override val argsSerializer = ToolArgs.Empty.serializer()

    override val descriptor = ToolDescriptor(
        name = "dummy",
        description = "Dummy tool for testing",
        requiredParameters = emptyList()
    )

    override suspend fun doExecute(args: ToolArgs.Empty): String = "Dummy result"
}

object CreateTool : SimpleTool<CreateTool.Args>() {
    @Serializable
    data class Args(val name: String) : ToolArgs

    override val argsSerializer = Args.serializer()

    override val descriptor = ToolDescriptor(
        name = "create",
        description = "Create something",
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "name",
                description = "Name of the entity to create",
                type = ToolParameterType.String
            )
        )
    )

    override suspend fun doExecute(args: Args): String = "created"
}

object SolveTool : SimpleTool<SolveTool.Args>() {
    @Serializable
    data class Args(val name: String) : ToolArgs

    override val argsSerializer = Args.serializer()

    override val descriptor = ToolDescriptor(
        name = "solve",
        description = "Solve something",
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "name",
                description = "Name of the entity to create",
                type = ToolParameterType.String
            )
        )
    )

    override suspend fun doExecute(args: Args): String = "solved"
}