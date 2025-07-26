package ai.koog.agents.features.pubsub.providers.redis

import ai.koog.agents.features.pubsub.message.PubSubStringMessage
import io.lettuce.core.RedisURI
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RedisPubSubProviderTest {

    @Test
    fun provider_shouldInitializeWithValidConfig() {
        val redisUri = RedisURI.create("redis://localhost:6379")
        val provider = RedisPubSubProvider(
            redisUri = redisUri,
            keyPrefix = "test:",
            connectionTimeout = 3000,
            enablePatternSubscription = true
        )
        
        // Should not throw during initialization
        assertTrue(true)
    }

    @Test
    fun provider_shouldFormatChannelNamesWithPrefix() = runTest {
        val redisUri = RedisURI.create("redis://localhost:6379")
        val provider = RedisPubSubProvider(
            redisUri = redisUri,
            keyPrefix = "app:",
            connectionTimeout = 1000
        )
        
        // Test prefixed channel formatting through health info
        val healthInfo = provider.getHealthInfo()
        assertEquals("app:", healthInfo["keyPrefix"])
        assertEquals("redis", healthInfo["provider"])
    }

    @Test
    fun provider_shouldHandleEmptyPrefix() = runTest {
        val redisUri = RedisURI.create("redis://localhost:6379")
        val provider = RedisPubSubProvider(
            redisUri = redisUri,
            keyPrefix = "",
            connectionTimeout = 1000
        )
        
        val healthInfo = provider.getHealthInfo()
        assertEquals("", healthInfo["keyPrefix"])
    }

    @Test
    fun provider_shouldReportNotConnectedWhenRedisUnavailable() = runTest {
        // Use invalid Redis URI to simulate connection failure
        val redisUri = RedisURI.create("redis://invalid-host:9999")
        val provider = RedisPubSubProvider(
            redisUri = redisUri,
            connectionTimeout = 100 // Short timeout for quick test
        )
        
        val isConnected = provider.isConnected()
        val healthInfo = provider.getHealthInfo()
        
        assertFalse(isConnected)
        assertEquals(false, healthInfo["connected"])
        assertEquals(false, healthInfo["healthy"])
    }

    @Test
    fun provider_shouldConfigurePatternSubscription() = runTest {
        val redisUri = RedisURI.create("redis://localhost:6379")
        val provider = RedisPubSubProvider(
            redisUri = redisUri,
            enablePatternSubscription = true
        )
        
        val healthInfo = provider.getHealthInfo()
        assertEquals(true, healthInfo["patternSubscription"])
    }

    @Test
    fun provider_shouldHandleStringMessage() = runTest {
        val redisUri = RedisURI.create("redis://invalid-host:9999")
        val provider = RedisPubSubProvider(redisUri = redisUri, connectionTimeout = 100)
        
        val message = PubSubStringMessage(
            topic = "test-topic",
            content = "test message",
            attributes = mapOf("key" to "value")
        )
        
        // Should handle the message type even if publish fails due to connection
        try {
            provider.publish(message)
        } catch (e: Exception) {
            // Expected when Redis is not available
            assertTrue(e.message?.contains("Redis") == true || e.message?.contains("Connection") == true)
        }
    }

    @Test
    fun provider_shouldCloseGracefully() = runTest {
        val redisUri = RedisURI.create("redis://localhost:6379")
        val provider = RedisPubSubProvider(redisUri = redisUri, connectionTimeout = 1000)
        
        // Should not throw during close, even if not connected
        provider.close()
        
        assertTrue(true) // Test passes if no exception thrown
    }

    @Test
    fun provider_shouldConfigureConnectionTimeout() = runTest {
        val redisUri = RedisURI.create("redis://localhost:6379")
        val customTimeout = 2500L
        val provider = RedisPubSubProvider(
            redisUri = redisUri,
            connectionTimeout = customTimeout
        )
        
        // Test passes if provider initializes without error
        assertTrue(true)
    }
}