package ai.koog.agents.features.tracing.mock

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.testing.tools.DummyTool
import ai.koog.serialization.typeToken
import kotlinx.serialization.Serializable

internal class RecursiveTool : SimpleTool<RecursiveTool.Args>(
    argsType = typeToken<Args>(),
    name = "recursive",
    description = "Recursive tool for testing"
) {
    @Serializable
    data class Args(val dummy: String = "")

    override suspend fun execute(args: Args): String {
        return "Dummy tool result: ${DummyTool().execute(DummyTool.Args())}"
    }
}
