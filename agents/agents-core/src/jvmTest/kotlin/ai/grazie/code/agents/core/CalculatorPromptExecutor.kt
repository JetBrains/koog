package ai.grazie.code.agents.core

import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.utils.json.JSON
import ai.jetbrains.code.prompt.dsl.Prompt
import ai.jetbrains.code.prompt.executor.model.PromptExecutor
import ai.jetbrains.code.prompt.message.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object CalculatorChatExecutor : PromptExecutor {
    private val plusAliases = listOf("add", "sum", "plus")

    override suspend fun execute(prompt: Prompt, tools: List<ToolDescriptor>): List<Message.Response> {
        val input = prompt.messages.filterIsInstance<Message.User>().joinToString("\n") { it.content }
        val numbers = input.split(Regex("[^0-9.]")).filter { it.isNotEmpty() }.map { it.toFloat() }
        val result = when {
            plusAliases.any { it in input } && tools.contains(CalculatorTools.PlusTool.descriptor) -> {
                Message.Tool.Call(
                    id = "1",
                    tool = CalculatorTools.PlusTool.name,
                    content = JSON.Default.string(
                        buildJsonObject {
                            put("a", numbers[0])
                            put("b", numbers[1])
                        }
                    )
                )
            }

            else -> Message.Assistant("Unknown operation")
        }
        return listOf(result)
    }

    override suspend fun executeStreaming(prompt: Prompt): Flow<String> = flow { emit(execute(prompt)) }
}
