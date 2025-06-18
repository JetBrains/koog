package ai.koog.agents.testing.tools

import ai.koog.agents.core.tools.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Simple tool implementation for testing purposes.
 * This tool accepts a dummy parameter and returns a constant result.
 */
public class DummyTool : SimpleTool<DummyTool.Args>() {
    /**
     * Represents the arguments for the DummyTool.
     *
     * @property dummy A dummy string parameter that can be optionally specified.
     */
    @Serializable
    public data class Args(val dummy: String = "") : ToolArgs

    override val argsSerializer: KSerializer<Args> = Args.serializer()

    override val descriptor: ToolDescriptor = ToolDescriptor(
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
