package ai.koog.prompt.executor.cached

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.cache.model.PromptCache
import ai.koog.prompt.cache.model.put
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.CacheControl
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.streamFrameFlowOf
import ai.koog.utils.time.KoogClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
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
    private class MockPromptExecutor : PromptExecutor() {
        var executeCalled = false
        var executeStreamingCalled = false

        override suspend fun execute(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>
        ): Message.Assistant {
            executeCalled = true
            return testResponse
        }

        override fun executeStreaming(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>
        ): Flow<StreamFrame> {
            executeStreamingCalled = true
            return streamFrameFlowOf("Streaming response from executor")
        }

        override suspend fun moderate(
            prompt: Prompt,
            model: LLModel
        ): ModerationResult {
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

    @Serializable
    private sealed interface SampleCacheControl : CacheControl {
        @Serializable
        data object Default : SampleCacheControl

        @Serializable
        data object OneHour : SampleCacheControl
    }

    @Serializable
    private sealed interface OtherProviderCacheControl : CacheControl {
        @Serializable
        data object Default : OtherProviderCacheControl
    }

    private fun promptWithCache(cache: CacheControl): Prompt = Prompt(
        messages = listOf(
            Message.System("You are a helpful assistant.", RequestMetaInfo.Empty, cache),
            Message.User("What is 2 + 2?", RequestMetaInfo.Empty),
        ),
        id = "repro",
    )

    @Test
    fun testExecuteWhenMessageCarriesCacheControl() = runTest {
        val cache = MockPromptCache()
        val executor = MockPromptExecutor()
        val cachedExecutor = CachedPromptExecutor(cache, executor, testClock)
        val prompt = promptWithCache(SampleCacheControl.Default)

        val response = cachedExecutor.execute(prompt, testModel, testTools)

        assertTrue(cache.getCalled, "Cache get should be called")
        assertTrue(executor.executeCalled, "Nested executor should run when the cache misses")
        assertTrue(cache.putCalled, "Cache put should store the response")
        assertEquals(testResponse, response, "Should return the nested executor response")

        executor.executeCalled = false
        cache.putCalled = false
        val cachedResponse = cachedExecutor.execute(prompt, testModel, testTools)

        assertEquals(false, executor.executeCalled, "Second call with the same cache directive should hit the cache")
        assertEquals(testResponse, cachedResponse, "Should return the cached response")
    }

    @Test
    fun testDifferentCacheControlsUseDifferentCacheKeys() = runTest {
        val defaultKey = PromptCache.Request.create(
            promptWithCache(SampleCacheControl.Default),
            testTools
        ).asCacheKey
        val oneHourKey = PromptCache.Request.create(
            promptWithCache(SampleCacheControl.OneHour),
            testTools
        ).asCacheKey
        val otherProviderKey = PromptCache.Request.create(
            promptWithCache(OtherProviderCacheControl.Default),
            testTools
        ).asCacheKey

        assertNotEquals(defaultKey, oneHourKey, "Different directives on the same type must not share a cache entry")
        assertNotEquals(
            defaultKey,
            otherProviderKey,
            "Directives with the same simple name from different providers must not share a cache entry"
        )
    }
}
