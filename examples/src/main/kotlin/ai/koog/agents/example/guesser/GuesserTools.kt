package ai.koog.agents.example.guesser

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import kotlinx.serialization.Serializable

abstract class GuesserTool(
    name: String,
    description: String,
) : SimpleTool<GuesserTool.Args>() {
    @Serializable
    data class Args(val value: Int) : ToolArgs

    final override val argsSerializer = Args.serializer()

    final override val descriptor = ToolDescriptor(
        name = name,
        description = description,
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "value",
                description = "A value to compare the guessed number with.",
                type = ToolParameterType.Integer,
            )
        )
    )

    protected fun ask(question: String, value: Int): String {
        print("$question [Y/n]: ")

        return when (readln().lowercase()) {
            "", "y", "yes" -> "YES"
            "n", "no" -> "NO"
            else -> {
                println("Invalid input! Please, try again.")
                ask(question, value)
            }
        }
    }
}

/**
 * 2. Implement the tool (tools).
 */

object LessThanTool : GuesserTool(
    name = "less_than",
    description = "Asks the user if his number is STRICTLY less than a given value.",
) {
    override suspend fun doExecute(args: Args): String {
        return ask("Is your number less than ${args.value}?", args.value)
    }
}

object GreaterThanTool : GuesserTool(
    name = "greater_than",
    description = "Asks the user if his number is STRICTLY greater than a given value.",
) {
    override suspend fun doExecute(args: Args): String {
        return ask("Is your number greater than ${args.value}?", args.value)
    }
}

object ProposeNumberTool : GuesserTool(
    name = "propose_number",
    description = "Asks the user if his number is EXACTLY equal to the given number. Only use this tool once you've narrowed down your answer.",
) {
    override suspend fun doExecute(args: Args): String {
        return ask("Is your number equal to ${args.value}?", args.value)
    }
}
