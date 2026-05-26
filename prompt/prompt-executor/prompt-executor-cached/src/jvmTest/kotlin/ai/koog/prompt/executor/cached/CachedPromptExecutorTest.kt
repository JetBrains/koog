package ai.koog.prompt.executor.cached

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.cache.model.PromptCache
import ai.koog.prompt.cache.model.put
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.cached.factory.CachedPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutorBuilder
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.streamFrameFlowOf
import ai.koog.utils.time.KoogClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CachedPromptExecutorTest {
    companion object {
        private val testPrompt = Prompt(listOf(Message.User("Hello, world!", RequestMetaInfo.Empty)), "test-prompt-id")
        private val testTools = emptyList<ToolDescriptor>()
        private val testResponse = Message.Assistant("Hello, user!", ResponseMetaInfo.Empty)
        private val testClock = KoogClock { testResponse.metaInfo.timestamp }
        private val testModel = LLModel(
            provider = object : LLMProvider("", "") {},
            id = "",
            capabilities = emptyList(),
            contextLength = 1_000L,
        )
    }

    // Mock implementation of PromptCache
    private class MockPromptCache : PromptCache {
        private val cache = mutableMapOf<String, Message.Assistant>()
        var getCalled = false
        var putCalled = false

        override suspend fun get(request: PromptCache.Request): Message.Assistant? {
            getCalled = true
            return cache[request.asCacheKey]
        }

        override suspend fun put(request: PromptCache.Request, response: Message.Assistant) {
            putCalled = true
            cache[request.asCacheKey] = response
        }
    }

    // Mock implementation of PromptExecutor
    private class MockPromptExecutorBuilder : PromptExecutorBuilder() {
        var executeCalled = false
        var executeStreamingCalled = false

        override suspend fun onExecute(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>
        ): Message.Assistant {
            executeCalled = true
            return testResponse
        }

        override fun onStreaming(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>
        ): Flow<StreamFrame> {
            executeStreamingCalled = true
            return streamFrameFlowOf("Streaming response from executor")
        }

        override suspend fun onModerate(
            prompt: Prompt,
            model: LLModel
        ): ModerationResult {
            throw UnsupportedOperationException("Moderation is not needed for TestLLMExecutor")
        }
    }

    @Test
    fun `test executor uses cache when cached result is available`() = runTest {
        // Arrange
        val cache = MockPromptCache()
        val executorBuilder = MockPromptExecutorBuilder()
        val cachedExecutor = CachedPromptExecutor(cache, executorBuilder.build(), testClock)

        cache.put(testPrompt, testTools, testResponse)
        val response = cachedExecutor.execute(testPrompt, testModel, testTools)

        assertTrue(cache.getCalled, "Cache get should be called")
        assertEquals(false, executorBuilder.executeCalled, "Executor should not be called when cache hit")
        assertEquals(testResponse, response, "Should return cached response")
    }

    @Test
    fun `test executor delegates to nested executor when no cached result is available`() = runTest {
        val cache = MockPromptCache()
        val executor = MockPromptExecutorBuilder()
        val cachedExecutor = CachedPromptExecutor(cache, executor.build(), testClock)

        val response = cachedExecutor.execute(testPrompt, testModel, testTools)

        assertTrue(cache.getCalled, "Cache get should be called")
        assertTrue(executor.executeCalled, "Executor should be called when cache miss")
        assertTrue(cache.putCalled, "Cache put should be called to store the result")
        assertEquals(testResponse, response, "Should return response from executor")
    }
}
