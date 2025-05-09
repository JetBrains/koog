package ai.grazie.code.agents.core.tools.tools

import ai.grazie.code.agents.core.tools.*
import kotlinx.serialization.Serializable

/**
 * The `SayToUser` allows agent to say something to the output (via `println`).
 */
object SayToUser : SimpleTool<SayToUser.Args>() {
    @Serializable
    data class Args(val message: String) : Tool.Args

    override val argsSerializer = Args.serializer()

    override val descriptor = ToolDescriptor(
        name = "__say_to_user__", description = "Service tool, used by the agent to talk.",
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "message", description = "Message from the agent", type = ToolParameterType.String
            ),
        ),
    )

    override suspend fun doExecute(args: Args): String {
        println("Agent says: ${args.message}")

        return "DONE"
    }
}
