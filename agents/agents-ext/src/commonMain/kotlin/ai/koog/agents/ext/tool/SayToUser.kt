package ai.koog.agents.ext.tool

import ai.koog.agents.core.tools.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * The `SayToUser` allows agent to say something to the output (via `println`).
 */
public object SayToUser : SimpleTool<SayToUser.Args>() {
    @Serializable
    public data class Args(val message: String) : Tool.Args

    override val argsSerializer: KSerializer<Args> = Args.serializer()

    override val descriptor: ToolDescriptor = ToolDescriptor(
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
