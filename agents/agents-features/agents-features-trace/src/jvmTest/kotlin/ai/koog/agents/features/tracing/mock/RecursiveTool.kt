package ai.koog.agents.features.tracing.mock

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.testing.tools.DummyTool
import kotlinx.serialization.Serializable

internal class RecursiveTool : SimpleTool<RecursiveTool.Args>() {
    @Serializable
    data class Args(val dummy: String = "") : ToolArgs

    override val argsSerializer = Args.serializer()

    override val descriptor = ToolDescriptor(
        name = "recursive",
        description = "Recursive tool for testing",
        requiredParameters = emptyList()
    )

    override suspend fun doExecute(args: Args): String {
        return "Dummy tool result: ${DummyTool().doExecute(DummyTool.Args())}"
    }
}
