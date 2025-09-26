package ai.koog.agents.ext.tool

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * The `SayToUser` allows agent to say something to the output (via `println`).
 */
public object SayToUser : SimpleTool<SayToUser.Args>() {
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

    override val argsSerializer: KSerializer<Args> = Args.serializer()
    override val name: String = "say_to_user"
    override val description: String = "Service tool, used by the agent to talk."

    override suspend fun doExecute(args: Args): String {
        println("Agent says: ${args.message}")
        return "DONE"
    }
}
