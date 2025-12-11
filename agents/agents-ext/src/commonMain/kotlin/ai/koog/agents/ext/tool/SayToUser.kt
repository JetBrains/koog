package ai.koog.agents.ext.tool

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable

/**
 * The `SayToUser` allows agent to say something to the output (via `println`).
 */
public object SayToUser : SimpleTool<SayToUser.Args>(
    argsSerializer = Args.serializer(),
    name = "say_to_user",
    description = "Service tool, used by the agent to talk."
) {
    /**
     * Represents the arguments for the [SayToUser] tool
     *
     * @property message A string representing a specific message or input payload
     * required for tool execution.
     */
    @Serializable
    public data class Args(
        @property:LLMDescription("Message from the agent")
        val message: String
    )

    override suspend fun execute(args: Args): String {
        println("Agent says: ${args.message}")
        return "DONE"
    }
}
