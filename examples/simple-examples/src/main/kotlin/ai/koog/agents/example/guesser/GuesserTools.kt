package ai.koog.agents.example.guesser

import ai.koog.agents.core.tools.SimpleTool
import kotlinx.serialization.Serializable

abstract class GuesserTool(
    toolName: String,
    toolDescription: String,
) : SimpleTool<GuesserTool.Args>() {
    @Serializable
    data class Args(val value: Int)

    final override val argsSerializer = Args.serializer()

    final override val name: String = toolName
    final override val description: String = toolDescription

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
    toolName = "less_than",
    toolDescription = "Asks the user if his number is STRICTLY less than a given value.",
) {
    override suspend fun doExecute(args: Args): String {
        return ask("Is your number less than ${args.value}?", args.value)
    }
}

object GreaterThanTool : GuesserTool(
    toolName = "greater_than",
    toolDescription = "Asks the user if his number is STRICTLY greater than a given value.",
) {
    override suspend fun doExecute(args: Args): String {
        return ask("Is your number greater than ${args.value}?", args.value)
    }
}

object ProposeNumberTool : GuesserTool(
    toolName = "propose_number",
    toolDescription = "Asks the user if his number is EXACTLY equal to the given number. Only use this tool once you've narrowed down your answer.",
) {
    override suspend fun doExecute(args: Args): String {
        return ask("Is your number equal to ${args.value}?", args.value)
    }
}
