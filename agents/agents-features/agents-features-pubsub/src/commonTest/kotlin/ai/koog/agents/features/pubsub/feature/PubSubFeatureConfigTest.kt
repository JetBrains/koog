package ai.koog.agents.features.pubsub.feature

import ai.koog.agents.features.pubsub.message.PubSubMessage
import ai.koog.agents.features.pubsub.message.PubSubStringMessage
import ai.koog.agents.features.pubsub.providers.NoPubSubProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PubSubFeatureConfigTest {

    @Test
    fun defaultConfig_shouldHaveExpectedDefaults() {
        val config = PubSubFeatureConfig()
        
        assertTrue(config.provider is NoPubSubProvider)
        assertEquals(emptyList(), config.autoSubscribeTopics)
        assertFalse(config.publishAgentEvents)
        assertFalse(config.publishToolEvents)
        assertFalse(config.publishLLMEvents)
        assertEquals("agent-events", config.agentEventTopic)
        assertEquals("tool-events", config.toolEventTopic)
        assertEquals("llm-events", config.llmEventTopic)
        assertTrue(config.autoAcknowledge)
        assertEquals(10, config.maxConcurrentMessages)
        assertEquals(emptyMap(), config.providerConfig)
    }

    @Test
    fun publishFilter_shouldDefaultToAcceptAll() {
        val config = PubSubFeatureConfig()
        
        val message = PubSubStringMessage("test", "content", emptyMap())
        assertTrue(config.publishFilter(message))
    }

    @Test
    fun receiveFilter_shouldDefaultToAcceptAll() {
        val config = PubSubFeatureConfig()
        
        val message = PubSubStringMessage("test", "content", emptyMap())
        assertTrue(config.receiveFilter(message))
    }

    @Test
    fun publishFilter_shouldBeConfigurable() {
        val config = PubSubFeatureConfig()
        
        // Configure filter to only accept messages with specific topic
        config.publishFilter = { message ->
            (message as? PubSubMessage)?.topic == "allowed-topic"
        }
        
        val allowedMessage = PubSubStringMessage("allowed-topic", "content", emptyMap())
        val blockedMessage = PubSubStringMessage("blocked-topic", "content", emptyMap())
        
        assertTrue(config.publishFilter(allowedMessage))
        assertFalse(config.publishFilter(blockedMessage))
    }

    @Test
    fun receiveFilter_shouldBeConfigurable() {
        val config = PubSubFeatureConfig()
        
        // Configure filter to only accept messages with specific attribute
        config.receiveFilter = { message ->
            message.attributes.containsKey("allowed")
        }
        
        val allowedMessage = PubSubStringMessage("test", "content", mapOf("allowed" to "true"))
        val blockedMessage = PubSubStringMessage("test", "content", mapOf("other" to "value"))
        
        assertTrue(config.receiveFilter(allowedMessage))
        assertFalse(config.receiveFilter(blockedMessage))
    }

    @Test
    fun config_shouldAllowCustomization() {
        val config = PubSubFeatureConfig()
        
        config.autoSubscribeTopics = listOf("topic1", "topic2")
        config.publishAgentEvents = true
        config.publishToolEvents = true
        config.publishLLMEvents = true
        config.agentEventTopic = "custom-agent-events"
        config.toolEventTopic = "custom-tool-events"
        config.llmEventTopic = "custom-llm-events"
        config.autoAcknowledge = false
        config.maxConcurrentMessages = 20
        config.providerConfig = mapOf("timeout" to 5000)
        
        assertEquals(listOf("topic1", "topic2"), config.autoSubscribeTopics)
        assertTrue(config.publishAgentEvents)
        assertTrue(config.publishToolEvents)
        assertTrue(config.publishLLMEvents)
        assertEquals("custom-agent-events", config.agentEventTopic)
        assertEquals("custom-tool-events", config.toolEventTopic)
        assertEquals("custom-llm-events", config.llmEventTopic)
        assertFalse(config.autoAcknowledge)
        assertEquals(20, config.maxConcurrentMessages)
        assertEquals(mapOf("timeout" to 5000), config.providerConfig)
    }
}