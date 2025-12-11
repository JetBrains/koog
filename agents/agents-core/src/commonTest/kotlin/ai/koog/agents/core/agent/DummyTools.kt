package ai.koog.agents.core.agent

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

object DummyTool : SimpleTool<Unit>(
    argsSerializer = Unit.serializer(),
    name = "dummy_tool",
    description = "Dummy tool for testing"
) {
    override suspend fun execute(args: Unit): String = "Dummy result"
}

object CreateTool : SimpleTool<CreateTool.Args>(
    argsSerializer = Args.serializer(),
    name = "create",
    description = "Create something"
) {
    @Serializable
    data class Args(
        @property:LLMDescription("Name of the entity to create") val name: String
    )

    override suspend fun execute(args: Args): String = "created"
}
