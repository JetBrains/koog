package ai.koog.prompt.cache.memory

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.cache.model.get
import ai.koog.prompt.cache.model.put
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class InMemoryPromptCacheTest {
    private lateinit var cache: InMemoryPromptCache
    private lateinit var smallCache: InMemoryPromptCache

    companion object {
        private fun createAssistantMessage(content: String) = Message.Assistant(content, ResponseMetaInfo.Empty)
        private fun createTestPrompt(messages: List<Message>) = Prompt(messages, "test-prompt-id")

        private val testUserMessage = Message.User("Hello, user!", RequestMetaInfo.Empty)
        private val testPrompt = createTestPrompt(listOf(testUserMessage))
        private val testTools = emptyList<ToolDescriptor>()
        private val testResponse = listOf(createAssistantMessage("Hello, user!"))
        private val updatedTestResponse = listOf(createAssistantMessage("Hello, user, updated!"))
        private val testToolCallResponse =
            listOf(Message.Tool.Call("test-id", "test-tool", "test-content", ResponseMetaInfo.Empty))

        private val testPrompts = (1..5).map { iter -> Prompt.build(testPrompt) { user("Hello, world! $iter") } }
        private val testResponses = (1..5).map { iter -> listOf(createAssistantMessage("Hello, user $iter")) }

        private val testClock = object : Clock {
            override fun now() = testResponse.first().metaInfo.timestamp
        }

        private val differentTestClock = object : Clock {
            override fun now() = testClock.now().plus(1.milliseconds)
        }
    }

    @BeforeTest
    fun setUp() {
        cache = InMemoryPromptCache(null)
        smallCache = InMemoryPromptCache(3)
    }

    @Test
    fun `test basic cache operations`() = runTest {
        // Put the response in the cache
        cache.put(testPrompt, testTools, testResponse)

        // Get the response from the cache
        val cachedResponse = cache.get(testPrompt, testTools, testClock)

        // Verify the response is correct
        assertNotNull(cachedResponse)
        assertEquals(testResponse, cachedResponse)
    }

    @Test
    fun `test cache size limit enforcement`() = runTest {
        // Put all responses in the cache
        testPrompts.zip(testResponses).forEach { (prompt, response) ->
            smallCache.put(prompt, testTools, response)
        }

        // Verify that the oldest entries are removed
        assertNull(smallCache.get(testPrompts[0], testTools, testClock), "Oldest entry should be removed")
        assertNull(smallCache.get(testPrompts[1], testTools, testClock), "Second oldest entry should be removed")
        assertNotNull(smallCache.get(testPrompts[2], testTools, testClock), "Third entry should still be in cache")
        assertNotNull(smallCache.get(testPrompts[3], testTools, testClock), "Fourth entry should still be in cache")
        assertNotNull(smallCache.get(testPrompts[4], testTools, testClock), "Fifth entry should still be in cache")
    }

    @Test
    fun `test least recently used entries are removed`() = runTest {
        // Put all responses in the cache
        testPrompts.dropLast(2).zip(testResponses).forEach { (prompt, response) ->
            smallCache.put(prompt, testTools, response)
            Thread.sleep(10)
        }

        // Access entries in a specific order to change their "last accessed" time
        smallCache.get(testPrompts[0], testTools, testClock) // Access the first entry
        smallCache.get(testPrompts[2], testTools, testClock) // Access the third entry
        // The second entry is now the least recently used

        // Add a new entry which should trigger removal of the least recently used entry
        smallCache.put(testPrompt, testTools, testResponse)

        // Verify that the least recently used entry was removed
        assertNotNull(
            smallCache.get(testPrompts[0], testTools, testClock),
            "Recently accessed entry should still be in cache"
        )
        assertNull(
            smallCache.get(testPrompts[1], testTools, testClock),
            "Least recently accessed entry should be removed"
        )
        assertNotNull(
            smallCache.get(testPrompts[2], testTools, testClock),
            "Recently accessed entry should still be in cache"
        )
        assertNotNull(smallCache.get(testPrompt, testTools, testClock), "Newly added entry should be in cache")
    }

    @Test
    fun `test different response types`() = runTest {
        // Create different types of responses
        val assistantResponse = testResponse
        val toolCallResponse = testToolCallResponse
        val mixedResponse = listOf(
            assistantResponse.first(),
            toolCallResponse.first()
        )

        // Test assistant response
        cache.put(testPrompt, testTools, assistantResponse)
        val cachedAssistantResponse = cache.get(testPrompt, testTools, testClock)
        assertNotNull(cachedAssistantResponse)
        assertEquals(assistantResponse, cachedAssistantResponse)

        // Test tool call response
        cache.put(testPrompt, testTools, toolCallResponse)
        val cachedToolCallResponse = cache.get(testPrompt, testTools, testClock)
        assertNotNull(cachedToolCallResponse)
        assertEquals(toolCallResponse, cachedToolCallResponse)

        // Test mixed response
        cache.put(testPrompt, testTools, mixedResponse)
        val cachedMixedResponse = cache.get(testPrompt, testTools, testClock)
        assertNotNull(cachedMixedResponse)
        assertEquals(mixedResponse, cachedMixedResponse)
    }

    @Test
    fun `test empty response list`() = runTest {
        // Put an empty response list in the cache
        val emptyResponse = emptyList<Message.Response>()
        cache.put(testPrompt, testTools, emptyResponse)

        // Get the response from the cache
        val cachedResponse = cache.get(testPrompt, testTools, testClock)

        // Verify the response is correct
        assertNotNull(cachedResponse)
        assertTrue(cachedResponse.isEmpty())
    }

    @Test
    fun `test updating existing cache entry`() = runTest {
        // Put the initial response in the cache
        cache.put(testPrompt, testTools, testResponse)

        // Verify the initial response is cached
        val cachedInitialResponse = cache.get(testPrompt, testTools, testClock)
        assertNotNull(cachedInitialResponse)
        assertEquals(testResponse, cachedInitialResponse)

        // Update the cache with a new response for the same prompt
        cache.put(testPrompt, testTools, updatedTestResponse)

        // Verify the updated response is cached
        val cachedUpdatedResponse = cache.get(testPrompt, testTools, testClock)
        assertNotNull(cachedUpdatedResponse)
        assertEquals(updatedTestResponse, cachedUpdatedResponse)
    }

    @Test
    fun `test cache retrieval with different timestamps`() = runTest {
        val originalPrompt = createTestPrompt(listOf(testUserMessage))

        val sameUserMessageDifferentTime =
            testUserMessage.copy(metaInfo = testUserMessage.metaInfo.copy(timestamp = differentTestClock.now()))
        val samePromptDifferentTime = createTestPrompt(listOf(sameUserMessageDifferentTime))

        cache.put(originalPrompt, testTools, testResponse)

        // Try to retrieve the cached response
        val cachedResponse = cache.get(samePromptDifferentTime, testTools, testClock)

        // Verify the response is retrieved successfully
        assertNotNull(cachedResponse, "Should retrieve cache entry despite different timestamps")
        assertEquals(testResponse, cachedResponse, "Retrieved response should match original")
    }

    @Test
    fun `test cache supports memory config`() {
        val cacheTypes = listOf("memory", "memory:", "memory:unlimited", "memory:100")
        val cacheNegativeTypes = listOf("redis", "file", "")

        cacheTypes.forEach {
            assertTrue(InMemoryPromptCache.supports(it), "Should support '$it' config")
        }

        cacheNegativeTypes.forEach {
            assertFalse(InMemoryPromptCache.supports(it), "Should not support '$it' config")
        }
    }

    @Test
    fun `test cache with no limit configurations`() {
        val cacheTypes = listOf("memory", "memory:", "memory:unlimited", "memory:UNLIMITED")

        cacheTypes.forEach { cacheType ->
            val cache = InMemoryPromptCache.create(cacheType)
            assertTrue(
                cache is InMemoryPromptCache,
                "Should create InMemoryPromptCache instance with $cacheType cache type."
            )
        }
    }

    @Test
    fun `test cache with numeric limit configurations`() {
        val configs = listOf(100, 1)

        configs.forEach {
            val cache = InMemoryPromptCache.create("memory:$it")
            assertTrue(cache is InMemoryPromptCache, "Should create InMemoryPromptCache instance with limit $it")
        }
    }

    @Test
    fun `test cache with invalid configurations`() {
        val configs = listOf("abc", "100.5", "-100", "0")

        configs.forEach {
            assertFailsWith<IllegalStateException> {
                InMemoryPromptCache.create("memory:$it")
            }
        }

        assertFailsWith<IllegalArgumentException> {
            InMemoryPromptCache.create("redis:100")
        }
    }

    @Test
    fun `cache with numeric limit functions correctly`() = runTest {
        val limitedCache = InMemoryPromptCache.create("memory:2")

        limitedCache.put(testPrompts[0], testTools, testResponses[0])
        limitedCache.put(testPrompts[1], testTools, testResponses[1])

        assertNotNull(limitedCache.get(testPrompts[0], testTools, testClock))
        assertNotNull(limitedCache.get(testPrompts[1], testTools, testClock))

        limitedCache.put(testPrompts[2], testTools, testResponses[2])

        assertNull(limitedCache.get(testPrompts[0], testTools, testClock), "Oldest entry should be removed")
        assertNotNull(limitedCache.get(testPrompts[1], testTools, testClock), "Second entry should still be in cache")
        assertNotNull(limitedCache.get(testPrompts[2], testTools, testClock), "Newest entry should be in cache")
    }

    @Test
    fun `cache with no limit functions correctly`() = runTest {
        val unlimitedCache = InMemoryPromptCache.create("memory:unlimited")

        testPrompts.zip(testResponses).forEach { (prompt, response) ->
            unlimitedCache.put(prompt, testTools, response)
        }

        testPrompts.zip(testResponses).forEach { (prompt, _) ->
            assertNotNull(
                unlimitedCache.get(prompt, testTools, testClock),
                "All entries should remain in unlimited cache"
            )
        }
    }
}
