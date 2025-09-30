package ai.koog.agents.ext.tool

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Object representation of a tool that provides an interface for agent-user interaction.
 * It allows the agent to ask the user for input (via `stdout`/`stdin`).
 */
public object AskUser : SimpleTool<AskUser.Args>() {
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

    override val name: String = "__ask_user__"
    override val description: String = "Service tool, used by the agent to talk with user"
    override val argsSerializer: KSerializer<Args> = Args.serializer()

    override suspend fun doExecute(args: Args): String {
        println(args.message)
        return readln()
    }
}
