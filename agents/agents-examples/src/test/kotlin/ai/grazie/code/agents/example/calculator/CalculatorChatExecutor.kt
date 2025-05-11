package ai.grazie.code.agents.example.calculator

import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.utils.json.JSON
import ai.jetbrains.code.prompt.dsl.Prompt
import ai.jetbrains.code.prompt.executor.model.PromptExecutor
import ai.jetbrains.code.prompt.llm.LLModel
import ai.jetbrains.code.prompt.message.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object CalculatorChatExecutor : PromptExecutor {
    private val plusAliases = listOf("add", "sum", "plus")
    private val minusAliases = listOf("subtract", "minus")
    private val multiplyAliases = listOf("multiply", "times")
    private val divideAliases = listOf("divide")

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

            minusAliases.any { alias -> input.contains(alias) } && tools.contains(CalculatorTools.MinusTool.descriptor) -> {
                Message.Tool.Call(
                    id = "2",
                    tool = CalculatorTools.MinusTool.name,
                    content = JSON.Default.string(
                        buildJsonObject {
                            put("a", numbers[0])
                            put("b", numbers[1])
                        }
                    )
                )
            }

            multiplyAliases.any { alias -> input.contains(alias) } && tools.contains(CalculatorTools.MultiplyTool.descriptor) -> {
                Message.Tool.Call(
                    id = "3",
                    tool = CalculatorTools.MultiplyTool.name,
                    content = JSON.Default.string(
                        buildJsonObject {
                            put("a", numbers[0])
                            put("b", numbers[1])
                        }
                    )
                )
            }

            divideAliases.any { alias -> input.contains(alias) } && tools.contains(CalculatorTools.DivideTool.descriptor) -> {
                Message.Tool.Call(
                    id = "4",
                    tool = CalculatorTools.DivideTool.name,
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
