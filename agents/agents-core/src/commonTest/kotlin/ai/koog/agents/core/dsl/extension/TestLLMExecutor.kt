package ai.koog.agents.core.dsl.extension

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.toStreamFrames
import ai.koog.utils.time.KoogClock
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Instant

class TestLLMExecutor : PromptExecutor() {

    private val logger = KotlinLogging.logger {}

    companion object {
        val testClock: KoogClock = KoogClock { Instant.parse("2023-01-01T00:00:00Z") }

        const val DEFAULT_ASSISTANT_RESPONSE = "Default test response"
    }

    // Track the number of TLDR messages created
    var tldrCount = 0
        private set

    // Track the number of fact-extraction responses produced
    var factCount = 0
        private set

    // Store the messages for inspection
    var messages: MutableList<Message> = mutableListOf()

    // Reset the state for a new test
    fun reset() {
        tldrCount = 0
        factCount = 0
        messages = mutableListOf()
    }

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Message.Assistant {
        return handlePrompt(prompt)
    }

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): Flow<StreamFrame> = flow {
        handlePrompt(prompt).toStreamFrames().forEach { emit(it) }
    }

    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel
    ): ModerationResult {
        throw UnsupportedOperationException("Moderation is not needed for TestLLMExecutor")
    }

    private fun handlePrompt(prompt: Prompt): Message.Assistant {
        prompt.messages.forEach { logger.debug { "Message: $it" } }

        // Store all messages for later inspection
        messages.addAll(prompt.messages)

        // For compression test, return a TLDR summary
        if (prompt.messages.any {
                it.parts.any { part ->
                    part is MessagePart.Text &&
                        part.text.contains("Create a comprehensive summary of this conversation")
                }
            }
        ) {
            tldrCount++
            val tldrResponse = Message.Assistant(
                "TLDR #$tldrCount: Summary of conversation history",
                metaInfo = ResponseMetaInfo.create(testClock)
            )
            messages.add(tldrResponse)
            return tldrResponse
        }

        // For FactRetrieval compression: return a structured JSON response
        if (prompt.messages.any { message ->
                message.parts.any { part ->
                    part is MessagePart.Text && part.text.contains("specialized information extractor")
                }
            }
        ) {
            factCount++
            val isMultiple = prompt.messages.any { message ->
                message.parts.any { part ->
                    part is MessagePart.Text &&
                        (part.text.contains("\"facts\" array") || part.text.contains("facts found"))
                }
            }
            val factResponse = if (isMultiple) {
                Message.Assistant(
                    """{"facts": [{"fact": "Extracted fact #$factCount A"}, {"fact": "Extracted fact #$factCount B"}]}""",
                    metaInfo = ResponseMetaInfo.create(testClock)
                )
            } else {
                Message.Assistant(
                    """{"fact": "Extracted single fact #$factCount"}""",
                    metaInfo = ResponseMetaInfo.create(testClock)
                )
            }
            messages.add(factResponse)
            return factResponse
        }

        val response = Message.Assistant(DEFAULT_ASSISTANT_RESPONSE, metaInfo = ResponseMetaInfo.create(testClock))
        messages.add(response)
        return response
    }

    override fun close() {}
}
