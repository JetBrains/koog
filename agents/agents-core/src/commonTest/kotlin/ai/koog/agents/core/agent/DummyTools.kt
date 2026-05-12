package ai.koog.agents.core.agent

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import kotlinx.serialization.Serializable

object DummyTool : SimpleTool<Unit>(
    argsType = typeToken<Unit>(),
    name = "dummy_tool",
    description = "Dummy tool for testing"
) {
    override suspend fun execute(args: Unit): String = "Dummy result"
}

object CreateTool : SimpleTool<CreateTool.Args>(
    argsType = typeToken<Args>(),
    name = "create",
    description = "Create something"
) {
    @Serializable
    data class Args(
        @property:LLMDescription("Name of the entity to create") val name: String
    )

    override suspend fun execute(args: Args): String = "created"
}
