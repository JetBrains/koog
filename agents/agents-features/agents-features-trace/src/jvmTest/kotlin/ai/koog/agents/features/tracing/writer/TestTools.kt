package ai.koog.agents.features.tracing.writer

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import kotlinx.serialization.Serializable

class DummyTool : SimpleTool<DummyTool.Args>() {
    @Serializable
    data class Args(val dummy: String = "") : Tool.Args

    override val argsSerializer = Args.serializer()

    override val descriptor = ToolDescriptor(
        name = "dummy",
        description = "Dummy tool for testing",
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "dummy",
                description = "Dummy parameter",
                type = ToolParameterType.String
            )
        )
    )

    override suspend fun doExecute(args: Args): String = "Dummy result"
}