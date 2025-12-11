package ai.koog.agents.testing.tools

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

/**
 * A simple implementation of the Tool class designed to handle string arguments and return string results.
 * The tool is used for testing purposes only.
 */
public object TestFinishTool : Tool<TestFinishTool.Args, String>(
    argsSerializer = serializer<Args>(),
    resultSerializer = serializer<String>(),
    name = "test_finish_tool",
    description = "test-finish-tool"
) {

    /**
     * Represents the arguments for a tool or function, typically used in serialization and description contexts.
     *
     * @property output A description of the finish output.
     */
    @Serializable
    public data class Args(
        @property:LLMDescription("Finish output") val output: String = ""
    )

    override suspend fun execute(args: Args): String {
        return args.output
    }
}
