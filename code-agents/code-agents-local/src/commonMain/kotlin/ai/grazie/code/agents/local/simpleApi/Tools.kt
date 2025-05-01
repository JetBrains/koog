package ai.grazie.code.agents.local.simpleApi

import ai.grazie.code.agents.core.tools.*
import kotlinx.serialization.KSerializer
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


/**
 * The `TalkTool` allows agent to say something to the output (via `println`) or read input from the user (via `readln`).
 */
object TalkTool : SimpleTool<TalkTool.Args>() {
    @Serializable
    data class Args(val message: String, val doPrint: Boolean?, val doInput: Boolean?) : Tool.Args

    override val argsSerializer = Args.serializer()

    override val descriptor = ToolDescriptor(
        name = "__talk__", description = "Service tool, used by the agent to talk.",
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "reasons",
                description = "Reasons for the message",
                type = ToolParameterType.String
            ),
            ToolParameterDescriptor(
                name = "message", description = "Message from the agent", type = ToolParameterType.String
            ),
        ),
        optionalParameters = listOf(
            ToolParameterDescriptor(
                name = "doPrint",
                description = "If true, the message will be printed to stdout",
                type = ToolParameterType.Boolean
            ),
            ToolParameterDescriptor(
                name = "doInput",
                description = "If true, the message will be read from stdin",
                type = ToolParameterType.Boolean
            ),
        )
    )

    override suspend fun doExecute(args: Args): String {
        if (args.doPrint == true)
            println("Agent says: ${args.message}")

        if (args.doInput == true) {
            print("Enter your question:")
            return readln()
        }

        return "DONE"
    }
}

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

object ExitTool : SimpleTool<ExitTool.Args>() {
    @Serializable
    data class Args(val message: String) : Tool.Args

    override suspend fun doExecute(args: Args): String {
        return "DONE"
    }

    override val argsSerializer: KSerializer<Args>
        get() = Args.serializer()

    override val descriptor: ToolDescriptor
        get() = ToolDescriptor(
            name = "__exit__",
            description = "Service tool, used by the agent to end conversation on user request or agent decision",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "message", description = "Final message of the agent", type = ToolParameterType.String
                )
            )
        )
}