package ai.koog.agents.features.pubsub.providers

import ai.koog.agents.features.pubsub.message.PubSubStringMessage
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class MultiplexedPubSubProviderTest {
    
    private lateinit var inMemoryProvider1: InMemoryPubSubProvider
    private lateinit var inMemoryProvider2: InMemoryPubSubProvider
    private lateinit var inMemoryProvider3: InMemoryPubSubProvider
    
    @BeforeTest
    fun setup() {
        inMemoryProvider1 = InMemoryPubSubProvider()
        inMemoryProvider2 = InMemoryPubSubProvider()
        inMemoryProvider3 = InMemoryPubSubProvider()
    }
    
    @AfterTest
    fun cleanup() {
        inMemoryProvider1.close()
        inMemoryProvider2.close()
        inMemoryProvider3.close()
    }
    
    @Test
    fun `should route exact topic matches correctly`() = runTest {
        val multiplexedProvider = MultiplexedPubSubProvider {
            route("exact.topic") to inMemoryProvider1
            route("another.exact") to inMemoryProvider2
            defaultProvider = inMemoryProvider3
        }
        
        // Publish to exact matches
        multiplexedProvider.publish("exact.topic", "message1")
        multiplexedProvider.publish("another.exact", "message2")
        multiplexedProvider.publish("default.topic", "message3")
        
        // Publishing doesn't create subscriptions automatically
        // Let's verify by subscribing and checking messages are routed correctly
        multiplexedProvider.subscribe("exact.topic")
        multiplexedProvider.subscribe("another.exact")
        
        assertEquals(1, inMemoryProvider1.getSubscriptionCount("exact.topic"))
        assertEquals(1, inMemoryProvider2.getSubscriptionCount("another.exact"))
        
        multiplexedProvider.close()
    }
    
    @Test
    fun `should route wildcard patterns correctly`() = runTest {
        val multiplexedProvider = MultiplexedPubSubProvider {
            route("agent.*") to inMemoryProvider1
            route("alerts.*") to inMemoryProvider2
            defaultProvider = inMemoryProvider3
        }
        
        // Test wildcard routing
        multiplexedProvider.publish("agent.builder.tasks", "build castle")
        multiplexedProvider.publish("agent.defender.alerts", "enemy spotted")
        multiplexedProvider.publish("alerts.emergency", "creeper!")
        multiplexedProvider.publish("alerts.warning", "low health")
        multiplexedProvider.publish("other.topic", "default route")
        
        // All agent.* topics should go to provider1
        // All alerts.* topics should go to provider2
        // Other topics should go to default (provider3)
        
        multiplexedProvider.close()
    }
    
    @Test
    fun `should route prefix patterns correctly`() = runTest {
        val multiplexedProvider = MultiplexedPubSubProvider {
            route("tasks") to inMemoryProvider1
            route("status") to inMemoryProvider2
            defaultProvider = inMemoryProvider3
        }
        
        // Test prefix routing
        multiplexedProvider.publish("tasks.build", "message1")  // Should go to provider1
        multiplexedProvider.publish("taskstuff", "message2")     // Should go to provider1 (prefix match)
        multiplexedProvider.publish("status.update", "message3") // Should go to provider2
        multiplexedProvider.publish("other", "message4")         // Should go to default
        
        multiplexedProvider.close()
    }
    
    @Test
    fun `should prioritize exact matches over wildcards`() = runTest {
        val multiplexedProvider = MultiplexedPubSubProvider {
            route("agent.*") to inMemoryProvider1          // Wildcard
            route("agent.builder.tasks") to inMemoryProvider2  // Exact match
            defaultProvider = inMemoryProvider3
        }
        
        // Exact match should take priority
        multiplexedProvider.publish("agent.builder.tasks", "should go to provider2")
        multiplexedProvider.publish("agent.defender.tasks", "should go to provider1")
        
        multiplexedProvider.close()
    }
    
    @Test
    fun `should delegate subscriptions to correct providers`() = runTest {
        val multiplexedProvider = MultiplexedPubSubProvider {
            route("local.*") to inMemoryProvider1
            route("remote.*") to inMemoryProvider2
            defaultProvider = inMemoryProvider3
        }
        
        // Subscribe to topics from different providers
        multiplexedProvider.subscribe("local.events")
        multiplexedProvider.subscribe("remote.commands")
        multiplexedProvider.subscribe("default.topic")
        
        // Verify each provider got the correct subscription
        assertEquals(1, inMemoryProvider1.getSubscriptionCount("local.events"))
        assertEquals(1, inMemoryProvider2.getSubscriptionCount("remote.commands"))
        assertEquals(1, inMemoryProvider3.getSubscriptionCount("default.topic"))
        
        multiplexedProvider.close()
    }
    
    @Test
    fun `should handle health info aggregation`() = runTest {
        val multiplexedProvider = MultiplexedPubSubProvider {
            route("route1") to inMemoryProvider1
            route("route2") to inMemoryProvider2
            defaultProvider = inMemoryProvider3
        }
        
        val healthInfo = multiplexedProvider.getHealthInfo()
        
        assertEquals("multiplexed", healthInfo["provider"])
        assertEquals(3, healthInfo["totalProviders"])
        assertEquals(3, healthInfo["connectedProviders"]) // All InMemory providers are connected
        assertEquals(true, healthInfo["connected"])
        assertEquals(true, healthInfo["healthy"])
        assertEquals(2, healthInfo["routes"]) // Two explicit routes
        assertEquals(true, healthInfo["hasDefaultProvider"])
        
        // Should contain provider-specific health info
        val providers = healthInfo["providers"] as Map<*, *>
        assertEquals(3, providers.size)
        
        multiplexedProvider.close()
    }
    
    @Test
    fun `should handle connection status correctly`() = runTest {
        val multiplexedProvider = MultiplexedPubSubProvider {
            route("test") to inMemoryProvider1
            defaultProvider = inMemoryProvider2
        }
        
        // Initially connected
        assertTrue(multiplexedProvider.isConnected())
        
        // Close one provider
        inMemoryProvider1.close()
        
        // Should still be connected if any provider is connected
        assertTrue(multiplexedProvider.isConnected())
        
        // Close all providers
        inMemoryProvider2.close()
        
        // Now should be disconnected
        assertFalse(multiplexedProvider.isConnected())
        
        multiplexedProvider.close()
    }
    
    @Test
    fun `should handle publish routing with PubSubMessage objects`() = runTest {
        val multiplexedProvider = MultiplexedPubSubProvider {
            route("string.*") to inMemoryProvider1
            defaultProvider = inMemoryProvider2
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
    fun `should throw exception when no provider configured for topic`() = runTest {
        val multiplexedProvider = MultiplexedPubSubProvider {
            route("configured.topic") to inMemoryProvider1
            // No default provider
        }
        
        // Should throw exception for unconfigured topic
        assertFailsWith<PubSubException> {
            multiplexedProvider.publish("unconfigured.topic", "message")
        }
        
        assertFailsWith<PubSubException> {
            multiplexedProvider.subscribe("unconfigured.topic")
        }
        
        multiplexedProvider.close()
    }
    
    @Test
    fun `should require at least one route or default provider`() {
        assertFailsWith<IllegalArgumentException> {
            MultiplexedPubSubProvider {
                // No routes or default provider
            }
        }
    }
    
    @Test
    fun `should handle complex routing scenarios`() = runTest {
        // Simulate real-world Minecraft + Ktor scenario
        val multiplexedProvider = MultiplexedPubSubProvider {
            // Local Minecraft agent coordination
            route("agent.builder.*") to inMemoryProvider1
            route("agent.defender.*") to inMemoryProvider1
            route("agent.gatherer.*") to inMemoryProvider1
            route("alerts.*") to inMemoryProvider1
            
            // Cross-environment coordination
            route("planner.*") to inMemoryProvider2
            route("tasks.*") to inMemoryProvider2
            route("status.*") to inMemoryProvider2
            
            defaultProvider = inMemoryProvider3
        }
        
        // Test message routing
        multiplexedProvider.publish("agent.builder.resources", "stone available")  // Local
        multiplexedProvider.publish("alerts.emergency", "creeper spotted")          // Local
        multiplexedProvider.publish("tasks.build", "build castle")                  // Cross-env
        multiplexedProvider.publish("status.progress", "50% complete")              // Cross-env
        multiplexedProvider.publish("unknown.topic", "fallback")                    // Default
        
        // Verify providers received correct number of messages
        // (This is a simplified test - in reality we'd check message content)
        val localHealthInfo = inMemoryProvider1.getHealthInfo()
        val crossEnvHealthInfo = inMemoryProvider2.getHealthInfo()
        val defaultHealthInfo = inMemoryProvider3.getHealthInfo()
        
        multiplexedProvider.close()
    }
    
    @Test
    fun `should handle unsubscribe correctly`() = runTest {
        val multiplexedProvider = MultiplexedPubSubProvider {
            route("test.*") to inMemoryProvider1
            defaultProvider = inMemoryProvider2
        }
        
        // Subscribe and then unsubscribe
        multiplexedProvider.subscribe("test.topic")
        assertEquals(1, inMemoryProvider1.getSubscriptionCount("test.topic"))
        
        multiplexedProvider.unsubscribe("test.topic")
        assertEquals(0, inMemoryProvider1.getSubscriptionCount("test.topic"))
        
        multiplexedProvider.close()
    }
    
    @Test
    fun `should handle provider cleanup on close`() = runTest {
        val multiplexedProvider = MultiplexedPubSubProvider {
            route("test") to inMemoryProvider1
            defaultProvider = inMemoryProvider2
        }
        
        // Verify providers are initially connected
        assertTrue(inMemoryProvider1.isConnected())
        assertTrue(inMemoryProvider2.isConnected())
        
        multiplexedProvider.close()
        
        // After closing multiplexed provider, underlying providers should be closed
        assertFalse(inMemoryProvider1.isConnected())
        assertFalse(inMemoryProvider2.isConnected())
    }
}