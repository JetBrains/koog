package ai.koog.agents.core.dsl.extension

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.toStreamFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Clock

class TestLLMExecutor : PromptExecutor {
    companion object {
        val testClock: Clock = object : Clock {
            override fun now(): kotlinx.datetime.Instant = kotlinx.datetime.Instant.parse("2023-01-01T00:00:00Z")
        }
    }

    // Track the number of TLDR messages created
    var tldrCount = 0
        private set

    // Store the messages for inspection
    val messages = mutableListOf<Message>()

    // Reset the state for a new test
    fun reset() {
        tldrCount = 0
        messages.clear()
    }

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
        return listOf(handlePrompt(prompt))
    }

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): Flow<StreamFrame> =
        flowOf(handlePrompt(prompt).toStreamFrame())

    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel
    ): ModerationResult {
        throw UnsupportedOperationException("Moderation is not needed for TestLLMExecutor")
    }

    private fun handlePrompt(prompt: Prompt): Message.Response {
        prompt.messages.forEach { println("[DEBUG_LOG] Message: ${it.content}") }

        // Store all messages for later inspection
        messages.addAll(prompt.messages)

        // For compression test, return a TLDR summary
        if (prompt.messages.any { it.content.contains("Create a comprehensive summary of this conversation") }) {
            tldrCount++
            val tldrResponse = Message.Assistant(
                "TLDR #$tldrCount: Summary of conversation history",
                metaInfo = ResponseMetaInfo.create(testClock)
            )
            messages.add(tldrResponse)
            return tldrResponse
        }

        val response = Message.Assistant("Default test response", metaInfo = ResponseMetaInfo.create(testClock))
        messages.add(response)
        return response
    }

    override fun close() {}
}
