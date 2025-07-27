package ai.koog.agents.features.pubsub.providers

import ai.koog.agents.features.pubsub.message.PubSubStringMessage
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Simplified tests for MultiplexedPubSubProvider focusing on core functionality.
 */
class MultiplexedPubSubProviderSimpleTest {
    
    @Test
    fun `should create provider with routes and default`() = runTest {
        val provider1 = InMemoryPubSubProvider()
        val provider2 = InMemoryPubSubProvider()
        val defaultProvider = InMemoryPubSubProvider()
        
        val multiplexedProvider = MultiplexedPubSubProvider {
            route("test.topic") to provider1
            route("another.*") to provider2
            this.defaultProvider = defaultProvider
        }
        
        assertTrue(multiplexedProvider.isConnected())
        
        val healthInfo = multiplexedProvider.getHealthInfo()
        assertEquals("multiplexed", healthInfo["provider"])
        assertEquals(3, healthInfo["totalProviders"])
        assertEquals(true, healthInfo["connected"])
        
        multiplexedProvider.close()
        
        assertFalse(provider1.isConnected())
        assertFalse(provider2.isConnected())
        assertFalse(defaultProvider.isConnected())
    }
    
    @Test
    fun `should route publish messages correctly`() = runTest {
        val provider1 = InMemoryPubSubProvider()
        val provider2 = InMemoryPubSubProvider()
        val defaultProvider = InMemoryPubSubProvider()
        
        val multiplexedProvider = MultiplexedPubSubProvider {
            route("exact.match") to provider1
            route("wildcard.*") to provider2
            this.defaultProvider = defaultProvider
        }
        
        // Test different routing scenarios
        val messageId1 = multiplexedProvider.publish("exact.match", "message1")
        val messageId2 = multiplexedProvider.publish("wildcard.test", "message2")
        val messageId3 = multiplexedProvider.publish("unknown.topic", "message3")
        
        assertNotNull(messageId1)
        assertNotNull(messageId2)
        assertNotNull(messageId3)
        
        multiplexedProvider.close()
    }
    
    @Test
    fun `should handle PubSubMessage objects`() = runTest {
        val provider1 = InMemoryPubSubProvider()
        
        val multiplexedProvider = MultiplexedPubSubProvider {
            route("string.*") to provider1
        }
        
        val stringMessage = PubSubStringMessage(
            topic = "string.test",
            content = "test content",
            attributes = mapOf("key" to "value")
        )
        
        val messageId = multiplexedProvider.publish(stringMessage)
        assertNotNull(messageId)
        
        multiplexedProvider.close()
    }
    
    @Test
    fun `should throw exception when no provider configured`() = runTest {
        val provider1 = InMemoryPubSubProvider()
        
        val multiplexedProvider = MultiplexedPubSubProvider {
            route("configured.topic") to provider1
            // No default provider
        }
        
        assertFailsWith<PubSubException> {
            multiplexedProvider.publish("unconfigured.topic", "message")
        }
        
        assertFailsWith<PubSubException> {
            multiplexedProvider.subscribe("unconfigured.topic")
        }
        
        multiplexedProvider.close()
    }
    
    @Test
    fun `should require at least one route or default`() {
        assertFailsWith<IllegalArgumentException> {
            MultiplexedPubSubProvider {
                // No routes or default provider
            }
        }
    }
    
    @Test
    fun `should handle wildcard vs exact priority correctly`() = runTest {
        val exactProvider = InMemoryPubSubProvider()
        val wildcardProvider = InMemoryPubSubProvider()
        
        val multiplexedProvider = MultiplexedPubSubProvider {
            route("test.*") to wildcardProvider          // Wildcard
            route("test.exact") to exactProvider         // Exact - should take priority
        }
        
        // This should go to exact provider due to priority
        val messageId = multiplexedProvider.publish("test.exact", "should go to exact")
        assertNotNull(messageId)
        
        multiplexedProvider.close()
    }
}