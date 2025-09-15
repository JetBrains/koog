package ai.koog.agents.core.agent

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

object DummyTool : SimpleTool<Unit>() {
    override val argsSerializer = Unit.serializer()

    override val name: String = "dummy"
    override val toolDescription: String = "Dummy tool for testing"

    override suspend fun doExecute(args: Unit): String = "Dummy result"
}

object CreateTool : SimpleTool<CreateTool.Args>() {
    @Serializable
    data class Args(
        @property:LLMDescription("Name of the entity to create") val name: String
    )

    override val argsSerializer = Args.serializer()

    override val name: String = "create"
    override val toolDescription: String = "Create something"

    override suspend fun doExecute(args: Args): String = "created"
}
