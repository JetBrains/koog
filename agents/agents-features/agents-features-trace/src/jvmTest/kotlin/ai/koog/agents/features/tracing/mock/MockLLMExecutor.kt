package ai.koog.agents.features.tracing.mock

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
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class MockLLMExecutor : PromptExecutor {

    private val clock: Clock = object : Clock {
        override fun now(): Instant = Instant.parse("2023-01-01T00:00:00Z")
    }

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
        return listOf(handlePrompt(prompt))
    }

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): Flow<StreamFrame> =
        flow { emit(handlePrompt(prompt).toStreamFrame()) }

    private fun handlePrompt(prompt: Prompt): Message.Response {
        val lastMessage = prompt.messages.last()
        if (lastMessage.content.contains("tool")) {
            return Message.Tool.Call(
                id = "0",
                tool = "Tool call",
                content = "{}",
                metaInfo = ResponseMetaInfo(timestamp = Instant.parse("2023-01-01T00:00:00Z"))
            )
        }

        return Message.Assistant(content = "Default test response", ResponseMetaInfo.create(clock))
    }

    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel
    ): ModerationResult {
        throw UnsupportedOperationException("Moderation is not needed for TestLLMExecutor")
    }
}
