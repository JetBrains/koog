package ai.koog.agents.core

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object CalculatorChatExecutor : PromptExecutor {
    private val json = Json {
        ignoreUnknownKeys = true
        allowStructuredMapKeys = true
    }

    private val plusAliases = listOf("add", "sum", "plus")

    val testClock: Clock = object : Clock {
        override fun now(): Instant = Instant.parse("2023-01-01T00:00:00Z")
    }

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
        val input = prompt.messages.filterIsInstance<Message.User>().joinToString("\n") { it.content }
        val numbers = input.split(Regex("[^0-9.]")).filter { it.isNotEmpty() }.map { it.toFloat() }
        val result = when {
            plusAliases.any { it in input } && tools.contains(CalculatorTools.PlusTool.descriptor) -> {
                Message.Tool.Call(
                    id = "1",
                    tool = CalculatorTools.PlusTool.name,
                    content = json.encodeToString(
                        buildJsonObject {
                            put("a", numbers[0])
                            put("b", numbers[1])
                        }
                    ),
                    metaInfo = ResponseMetaInfo.create(testClock)
                )
            }

            else -> Message.Assistant("Unknown operation", metaInfo = ResponseMetaInfo.create(testClock))
        }
        return listOf(result)
    }

    override fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> =
        flow {
            try {
                val response = execute(prompt, model).single()
                emit(response.content)
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                println("[DEBUG_LOG] Error while emitting response: ${t::class.simpleName}(${t.message})")
            }
        }

    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel
    ): ModerationResult {
        throw UnsupportedOperationException("Moderation is not needed for CalculatorExecutor")
    }
}
