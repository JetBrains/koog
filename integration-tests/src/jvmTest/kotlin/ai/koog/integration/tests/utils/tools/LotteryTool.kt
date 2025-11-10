package ai.koog.integration.tests.utils.tools

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

object LotteryTool : Tool<List<List<Int>>, List<Int>>() {
    override val name = "lottery_picker"
    override val description =
        "A tool that by lottery tickets (list of 5 number from 1 to 100) picks the ids of winning tickets. " +
            "Winning ticket is the ticket which contains a winning number."

    override val argsSerializer = ListSerializer(ListSerializer(Int.serializer()))
    override val resultSerializer = ListSerializer(Int.serializer())

    override val descriptor = ToolDescriptor(
        name = name,
        description = description,
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "numbers",
                description = "A list of the lottery tickets. Each ticket is a list of 5 numbers from 1 to 100.",
                type = ToolParameterType.List(ToolParameterType.List(ToolParameterType.Integer))
            )
        )
    )

    override suspend fun execute(args: List<List<Int>>): List<Int> {
        val winnerValue = (1..100).random()
        return args.mapIndexedNotNull { index, values ->
            if (winnerValue in values) index else null
        }
    }
}
