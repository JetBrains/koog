package ai.koog.agents.testing.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Simple tool implementation for testing purposes.
 * This tool accepts a placeholder parameter and returns a constant result.
 */
public class DummyTool : SimpleTool<DummyTool.Args>() {

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

    override val argsSerializer: KSerializer<Args> = Args.serializer()

    override val name: String = "dummy"
    override val description: String = "Dummy tool for testing"

    override suspend fun doExecute(args: Args): String = result
}
