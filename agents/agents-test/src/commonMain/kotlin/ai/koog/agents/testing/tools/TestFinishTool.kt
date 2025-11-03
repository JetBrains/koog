package ai.koog.agents.testing.tools

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

/**
 * A simple implementation of the Tool class designed to handle string arguments and return string results.
 * The tool is used for testing purposes only.
 */
public object TestFinishTool : Tool<TestFinishTool.Args, String>() {

    /**
     * Represents the arguments for a tool or function, typically used in serialization and description contexts.
     *
     * @property output A description of the finish output.
     */
    @Serializable
    public data class Args(
        @property:LLMDescription("Finish output") val output: String = ""
    )

    override val argsSerializer: KSerializer<Args> = serializer<Args>()

    override val resultSerializer: KSerializer<String> = serializer<String>()

    override val description: String = "test-finish-tool"

    override suspend fun execute(args: Args): String {
        return args.output
    }
}
