package ai.koog.agents.ext.tool

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
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
    public data class Args(val message: String) : ToolArgs

    override val argsSerializer: KSerializer<Args> = Args.serializer()

    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = "__ask_user__",
        description = "Service tool, used by the agent to talk with user",
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "message",
                description = "Message from the agent",
                type = ToolParameterType.String
            )
        )
    )

    override suspend fun doExecute(args: Args): String {
        println(args.message)
        return readln()
    }
}
