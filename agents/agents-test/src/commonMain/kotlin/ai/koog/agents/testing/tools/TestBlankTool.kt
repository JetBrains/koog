package ai.koog.agents.testing.tools

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import kotlinx.serialization.Serializable

/**
 * A simple implementation of the `Tool` class that returns the input string argument as the result.
 * This tool is used for testing purposes only.
 *
 * @param name An optional name for the tool. If not provided, it defaults to "blank-tool".
 */
public class TestBlankTool(name: String? = null) : Tool<TestBlankTool.Args, String>(
    argsType = typeToken<Args>(),
    resultType = typeToken<String>(),
    name = name ?: "blank-tool",
    description = "test-finish-tool"
) {

    /**
     * Represents the arguments for the `TestBlankTool`.
     *
     * @property args A placeholder string parameter representing input for the tool. Defaults to an empty string.
     */
    @Serializable
    public data class Args(
        @property:LLMDescription("Blank parameter") val args: String = ""
    )

    override suspend fun execute(args: Args): String {
        return args.args
    }
}
