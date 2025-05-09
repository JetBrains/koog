package ai.grazie.code.agents.testing.tools

import ai.grazie.code.agents.core.tools.*
import kotlinx.serialization.Serializable

/**
 * Simple tool implementation for testing purposes.
 * This tool accepts a dummy parameter and returns a constant result.
 */
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