package ai.koog.agents.core

import ai.koog.agents.core.tools.ToolDescriptor
import ai.grazie.utils.json.JSON
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object CalculatorChatExecutor : PromptExecutor {
    private val plusAliases = listOf("add", "sum", "plus")

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
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

    override suspend fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> = flow { emit(execute(prompt, model)) }
}
