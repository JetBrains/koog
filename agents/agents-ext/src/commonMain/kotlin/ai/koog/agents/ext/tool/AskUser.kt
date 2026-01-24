package ai.koog.agents.ext.tool

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable

/**
 * Object representation of a tool that provides an interface for agent-user interaction.
 * It allows the agent to ask the user for input (via `stdout`/`stdin`).
 */
public object AskUser : SimpleTool<AskUser.Args>(
    argsSerializer = Args.serializer(),
    name = "__ask_user__",
    description = "Service tool, used by the agent to talk with user"
) {
    /**
     * Represents the arguments for the [AskUser] tool
     *
     * @property message The message to be used as an argument for the tool's execution.
     */
    @Serializable
    public data class Args(
        @property:LLMDescription("Message from the agent")
        val message: String
    )

    override suspend fun execute(args: Args): String {
        println(args.message)
        return readln()
    }
}
