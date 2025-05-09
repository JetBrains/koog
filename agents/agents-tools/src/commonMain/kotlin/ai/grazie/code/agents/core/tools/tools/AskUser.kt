package ai.grazie.code.agents.core.tools.tools

import ai.grazie.code.agents.core.tools.*
import kotlinx.serialization.Serializable

/**
 * Object representation of a tool that provides an interface for agent-user interaction.
 * It allows the agent to ask the user for input (via `stdout`/`stdin`).
 */
object AskUser : SimpleTool<AskUser.Args>() {
    @Serializable
    data class Args(val message: String) : Tool.Args

    override val argsSerializer = Args.serializer()

    override val descriptor = ToolDescriptor(
        name = "__ask_user__",
        description = "Service tool, used by the agent to talk with user",
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "message", description = "Message from the agent", type = ToolParameterType.String
            )
        )
    )

    override suspend fun doExecute(args: Args): String {
        println(args.message)
        return readln()
    }
}
