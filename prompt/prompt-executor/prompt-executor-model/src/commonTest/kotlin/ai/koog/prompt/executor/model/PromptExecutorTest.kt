package ai.koog.prompt.executor.model

import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PromptExecutorTest {

    private class FakePromptExecutor : PromptExecutor {
        override suspend fun execute(
            prompt: Prompt,
            model: LLModel,
            tools: List<ai.koog.agents.core.tools.ToolDescriptor>
        ): List<Message.Response> = TODO()

        override fun executeStreamingFrames(
            prompt: Prompt,
            model: LLModel,
            tools: List<ai.koog.agents.core.tools.ToolDescriptor>
        ): Flow<StreamFrame> {
            return flow {
                emit(StreamFrame.Append("hello"))
                emit(StreamFrame.End())
            }
        }

        override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult = TODO()
    }

    @Test
    fun `executeStreaming should call executeStreamingFrames`() = runTest {
        val executor = FakePromptExecutor()
        val model = LLModel(
            provider = LLMProvider.OpenAI,
            id = "test-model",
            capabilities = emptyList(),
            contextLength = 4096,
            maxOutputTokens = null,
        )

        val collected = mutableListOf<String>()
        executor.executeStreaming(
            prompt = Prompt.Empty,
            model = model,
            tools = emptyList()
        ).collect { collected.add(it) }

        assertEquals(
            expected = listOf("hello"),
            actual = collected,
            message = "Text emitted by executeStreamingFrames should be propagated by executeStreaming"
        )
    }
}
