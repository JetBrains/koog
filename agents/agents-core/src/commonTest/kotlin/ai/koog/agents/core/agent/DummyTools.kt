package ai.koog.agents.core.agent

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

object DummyTool : SimpleTool<Unit>() {
    override val argsSerializer = Unit.serializer()

    override val descriptor = ToolDescriptor(
        name = "dummy",
        description = "Dummy tool for testing",
        requiredParameters = emptyList()
    )

    override suspend fun doExecute(args: Unit): String = "Dummy result"
}

object CreateTool : SimpleTool<CreateTool.Args>() {
    @Serializable
    data class Args(val name: String)

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
