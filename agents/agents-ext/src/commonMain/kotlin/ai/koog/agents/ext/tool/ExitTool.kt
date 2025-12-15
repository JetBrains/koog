package ai.koog.agents.ext.tool

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable

/**
 * An object representing the exit tool, primarily intended for ending conversations upon user request
 * or based on agent decision. This tool finalizes interactions with a provided message.
 *
 * The tool utilizes a structured set of arguments, which includes the final message of the agent
 * to provide closure to the conversation. It returns the result as a standardized string, signaling
 * the execution has been completed.
 *
 * The descriptor defines the tool's metadata including its name, description, and required parameters.
 */
public object ExitTool : SimpleTool<ExitTool.Args>(
    argsSerializer = Args.serializer(),
    name = "__exit__",
    description = "Service tool, used by the agent to end conversation on user request or agent decision"
) {
    /**
     * Represents the arguments for the [ExitTool] tool
     *
     * @property message The input message provided as an argument for the tool.
     */
    @Serializable
    public data class Args(
        @property:LLMDescription("Final message of the agent")
        val message: String
    )

    override suspend fun execute(args: Args): String {
        return "DONE"
    }
}
