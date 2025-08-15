@file:OptIn(ExperimentalLettuceCoroutinesApi::class)

package ai.koog.prompt.cache.redis

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.cache.model.get
import ai.koog.prompt.cache.model.put
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class RedisPromptCacheTest {
    companion object {
        private val mockConnection: StatefulRedisConnection<String, String> = mockk(relaxed = true)
        private val mockCommands: RedisCoroutinesCommands<String, String> = mockk(relaxed = true)

        private fun createCache(ttl: Duration): RedisPromptCache {
            val mockRedisClient = MockRedisClient(mockConnection, mockCommands)

            // Create a RedisPromptCache with the mock client
            return RedisPromptCache(mockRedisClient, "test:", ttl)
        }

        private val testPrompt = Prompt(listOf(Message.User("Hello, world!", RequestMetaInfo.Empty)), "test-prompt-id")
        private val testTools = emptyList<ToolDescriptor>()
        private val testResponse = listOf(Message.Assistant("Hello, user!", ResponseMetaInfo.Empty))

        private val testClock = object : Clock {
            override fun now() = testResponse.first().metaInfo.timestamp
        }
    }

    @BeforeTest
    fun setUp() {
        // Replace the Kotlin extension function at runtime
        mockkStatic(StatefulRedisConnection<*, *>::coroutines)
        every { mockConnection.coroutines() } returns mockCommands
    }

    @AfterTest
    fun tearDown() {
        // Clean up so other tests aren't affected
        unmockkStatic(StatefulRedisConnection<*, *>::coroutines)
    }

    @Test
    fun `test put and get`() = runTest {
        val cache = createCache(60.seconds)
        cache.put(testPrompt, testTools, testResponse)

        val cachedResponse = cache.get(testPrompt, testTools, testClock)
        assertEquals(testResponse, cachedResponse)
    }

    @Test
    fun `test cache expiration`() = runTest {
        val cache = createCache(1.seconds)
        cache.put(testPrompt, testTools, testResponse)

        Thread.sleep(1500) // 1.5 seconds

        val cachedResponse = cache.get(testPrompt, testTools, testClock)
        assertNull(cachedResponse)
    }

    @DisabledOnOs(OS.WINDOWS, disabledReason = "Fails on Windows")
    @Test
    fun `test expiration update on access`() = runTest {
        val cache = createCache(2.seconds)
        cache.put(testPrompt, testTools, testResponse)

        Thread.sleep(1000) // 1 second

        val cachedResponse1 = cache.get(testPrompt, testTools, testClock)
        assertEquals(testResponse, cachedResponse1)

        Thread.sleep(1500) // 1.5 seconds (total 2.5 seconds from start)

        // The cache should still be valid because the timestamp was updated
        val cachedResponse2 = cache.get(testPrompt, testTools, testClock)
        assertEquals(testResponse, cachedResponse2)
    }
}
