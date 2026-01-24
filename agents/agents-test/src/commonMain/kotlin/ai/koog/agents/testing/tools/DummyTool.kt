package ai.koog.agents.testing.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable

/**
 * Simple tool implementation for testing purposes.
 * This tool accepts a placeholder parameter and returns a constant result.
 */
public class DummyTool : SimpleTool<DummyTool.Args>(
    argsSerializer = Args.serializer(),
    name = "dummy",
    description = "Dummy tool for testing"
) {

    /**
     * A constant value representing the default result returned by the DummyTool.
     */
    public val result: String = "Dummy result"

    /**
     * Represents the arguments for the DummyTool.
     *
     * @property dummy A placeholder string parameter that can be optionally specified.
     */
    @Serializable
    public data class Args(
        @property:LLMDescription("Dummy parameter")
        val dummy: String = ""
    )

    override suspend fun execute(args: Args): String = result
}
