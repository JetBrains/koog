package ai.koog.prompt.executor.cached

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.cache.model.PromptCache
import ai.koog.prompt.cache.model.put
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.ExecuteHook
import ai.koog.prompt.executor.model.InitialExecutionIntent
import ai.koog.prompt.executor.model.ModerationHook
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.model.PromptExecutorHooksHelper.executeWithHook
import ai.koog.prompt.executor.model.PromptExecutorHooksHelper.streamingWithHook
import ai.koog.prompt.executor.model.ResolvedExecutionIntent
import ai.koog.prompt.executor.model.StreamingExecutorHook
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.streamFrameFlowOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock

class CachedPromptExecutorTest {
    companion object {
        private val testPrompt = Prompt(listOf(Message.User("Hello, world!", RequestMetaInfo.Empty)), "test-prompt-id")
        private val testTools = emptyList<ToolDescriptor>()
        private val testResponse = listOf(Message.Assistant("Hello, user!", ResponseMetaInfo.Empty))
        private val testClock = object : Clock {
            override fun now() = testResponse.first().metaInfo.timestamp
        }
        private val testModel = LLModel(
            provider = object : LLMProvider("", "") {},
            id = "",
            capabilities = emptyList(),
            contextLength = 1_000L,
        )
    }

    // Mock implementation of PromptCache
    private class MockPromptCache : PromptCache {
        private val cache = mutableMapOf<String, List<Message.Response>>()
        var getCalled = false
        var putCalled = false

        override suspend fun get(request: PromptCache.Request): List<Message.Response>? {
            getCalled = true
            return cache[request.asCacheKey]
        }

        override suspend fun put(request: PromptCache.Request, response: List<Message.Response>) {
            putCalled = true
            cache[request.asCacheKey] = response
        }
    }

    // Mock implementation of PromptExecutor
    private class MockPromptExecutor : PromptExecutor() {
        var executeCalled = false
        var executeStreamingCalled = false

        override suspend fun execute(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>,
            hook: ExecuteHook?
        ): List<Message.Response> = executeWithHook(
            initialIntent = InitialExecutionIntent(prompt, tools, model),
            hook = hook
        ) {
            executeCalled = true
            testResponse
        }

        override fun executeStreaming(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>,
            hook: StreamingExecutorHook?
        ): Flow<StreamFrame> = streamingWithHook(
            initialIntent = InitialExecutionIntent(prompt, tools, model),
            hook = hook
        ) {
            executeStreamingCalled = true
            streamFrameFlowOf("Streaming response from executor")
        }

        override suspend fun moderate(prompt: Prompt, model: LLModel, hook: ModerationHook?): ModerationResult {
            throw UnsupportedOperationException("Moderation is not needed for TestLLMExecutor")
        }

        override fun close() {}
    }

    @Test
    fun `test executor uses cache when cached result is available`() = runTest {
        // Arrange
        val cache = MockPromptCache()
        val executor = MockPromptExecutor()
        val cachedExecutor = CachedPromptExecutor(cache, executor, testClock)

        cache.put(testPrompt, testTools, testResponse)
        val response = cachedExecutor.execute(testPrompt, testModel, testTools)

        assertTrue(cache.getCalled, "Cache get should be called")
        assertEquals(false, executor.executeCalled, "Executor should not be called when cache hit")
        assertEquals(testResponse, response, "Should return cached response")
    }

    @Test
    fun `test executor delegates to nested executor when no cached result is available`() = runTest {
        val cache = MockPromptCache()
        val executor = MockPromptExecutor()
        val cachedExecutor = CachedPromptExecutor(cache, executor, testClock)

        val response = cachedExecutor.execute(testPrompt, testModel, testTools)

        assertTrue(cache.getCalled, "Cache get should be called")
        assertTrue(executor.executeCalled, "Executor should be called when cache miss")
        assertTrue(cache.putCalled, "Cache put should be called to store the result")
        assertEquals(testResponse, response, "Should return response from executor")
    }

    @Test
    fun testHooksNotCalledOnCacheHit() = runTest {
        // Given
        val cache = MockPromptCache()
        val executor = MockPromptExecutor()
        val cachedExecutor = CachedPromptExecutor(cache, executor, testClock)

        // And
        var hookCalled = false
        val hooks = executeTrackingHooks { hookCalled = true }

        // When
        cache.put(testPrompt, testTools, testResponse)

        // And
        val result = cachedExecutor.execute(testPrompt, testModel, testTools, hooks)

        // Then
        assertFalse(executor.executeCalled, "Nested executor must not be called on cache hit")
        assertFalse(hookCalled, "Hooks must not be called on cache hit")
        assertEquals(testResponse, result)
    }

    @Test
    fun testHooksCalledOnCacheMiss() = runTest {
        // Given
        val cache = MockPromptCache()
        val executor = MockPromptExecutor()
        val cachedExecutor = CachedPromptExecutor(cache, executor, testClock)

        // And
        var hookCalled = false
        val hooks = executeTrackingHooks {
            hookCalled = true
        }

        // When
        val result = cachedExecutor.execute(testPrompt, testModel, testTools, hooks)

        // Then
        assertTrue(executor.executeCalled, "Nested executor must be called on cache miss")
        assertTrue(hookCalled, "Hooks must be called on cache miss")
        assertEquals(testResponse, result)
    }

    /** Creates an [ExecuteHook] that calls [onCompleted] when execute completes successfully. */
    private fun executeTrackingHooks(onCompleted: () -> Unit): ExecuteHook = object : ExecuteHook {
        override suspend fun onCompleted(
            intent: ResolvedExecutionIntent,
            effectiveModel: LLModel,
            result: List<Message.Response>
        ) =
            onCompleted()
    }
}
