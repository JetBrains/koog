package ai.koog.agents.features.pubsub.integration

import ai.koog.agents.features.common.message.FeatureMessage
import ai.koog.agents.features.common.message.FeatureMessageProcessor
import ai.koog.agents.features.pubsub.feature.PubSub
import ai.koog.agents.features.pubsub.feature.PubSubFeatureConfig
import ai.koog.agents.features.pubsub.message.MessagePublishedEvent
import ai.koog.agents.features.pubsub.message.MessageReceivedEvent
import ai.koog.agents.features.pubsub.message.PubSubStringMessage
import ai.koog.agents.features.pubsub.providers.NoPubSubProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PubSubIntegrationTest {

    @Test
    fun pubSubFeature_shouldCreateValidInitialConfig() {
        val config = PubSub.Feature.createInitialConfig()
        
        assertTrue(config is PubSubFeatureConfig)
        assertTrue(config.provider is NoPubSubProvider)
        assertEquals(emptyList(), config.autoSubscribeTopics)
    }

    @Test
    fun pubSubFeature_shouldHaveCorrectStorageKey() {
        val key = PubSub.Feature.key
        
        assertEquals("agents-features-pubsub", key.name)
    }

    @Test
    fun messageProcessor_shouldHandlePublishEvents() = runTest {
        val receivedMessages = mutableListOf<Any>()
        
        val processor = object : FeatureMessageProcessor() {
            override suspend fun processMessage(message: FeatureMessage) {
                receivedMessages.add(message)
            }
            override suspend fun close() {}
        }
        
        val publishEvent = MessagePublishedEvent(
            eventId = "event-123",
            topic = "test-topic",
            messageId = "msg-456"
        )
        
        processor.processMessage(publishEvent)
        
        assertEquals(1, receivedMessages.size)
        assertTrue(receivedMessages[0] is MessagePublishedEvent)
        
        val received = receivedMessages[0] as MessagePublishedEvent
        assertEquals("event-123", received.eventId)
        assertEquals("test-topic", received.topic)
        assertEquals("msg-456", received.messageId)
    }

    @Test
    fun messageProcessor_shouldHandleReceiveEvents() = runTest {
        val receivedMessages = mutableListOf<Any>()
        
        val processor = object : FeatureMessageProcessor() {
            override suspend fun processMessage(message: FeatureMessage) {
                receivedMessages.add(message)
            }
            override suspend fun close() {}
        }
        
        val receiveEvent = MessageReceivedEvent(
            eventId = "event-789",
            topic = "test-topic",
            messageId = "msg-012",
            content = "test content",
            attributes = mapOf("source" to "agent")
        )
        
        processor.processMessage(receiveEvent)
        
        assertEquals(1, receivedMessages.size)
        assertTrue(receivedMessages[0] is MessageReceivedEvent)
        
        val received = receivedMessages[0] as MessageReceivedEvent
        assertEquals("event-789", received.eventId)
        assertEquals("test-topic", received.topic)
        assertEquals("msg-012", received.messageId)
        assertEquals("test content", received.content)
        assertEquals(mapOf("source" to "agent"), received.attributes)
    }

    @Test
    fun config_shouldSupportMessageFiltering() {
        val config = PubSubFeatureConfig()
        
        // Configure filters
        config.publishFilter = { message ->
            message is PubSubStringMessage && message.topic.startsWith("allowed")
        }
        
        config.receiveFilter = { message ->
            message.attributes.containsKey("priority")
        }
        
        // Test publish filter
        val allowedMessage = PubSubStringMessage("allowed-topic", "content", emptyMap())
        val blockedMessage = PubSubStringMessage("blocked-topic", "content", emptyMap())
        
        assertTrue(config.publishFilter(allowedMessage))
        assertTrue(!config.publishFilter(blockedMessage))
        
        // Test receive filter
        val priorityMessage = PubSubStringMessage("test", "content", mapOf("priority" to "high"))
        val normalMessage = PubSubStringMessage("test", "content", emptyMap())
        
        assertTrue(config.receiveFilter(priorityMessage))
        assertTrue(!config.receiveFilter(normalMessage))
    }

    @Test
    fun config_shouldSupportMultipleSubscriptions() {
        val config = PubSubFeatureConfig()
        
        config.autoSubscribeTopics = listOf(
            "agent-commands", 
            "system-events", 
            "notifications"
        )
        
        assertEquals(3, config.autoSubscribeTopics.size)
        assertTrue(config.autoSubscribeTopics.contains("agent-commands"))
        assertTrue(config.autoSubscribeTopics.contains("system-events"))
        assertTrue(config.autoSubscribeTopics.contains("notifications"))
    }

    @Test
    fun config_shouldSupportEventPublishingConfiguration() {
        val config = PubSubFeatureConfig()
        
        // Configure all event types
        config.publishAgentEvents = true
        config.publishToolEvents = true
        config.publishLLMEvents = true
        
        // Configure custom topics
        config.agentEventTopic = "custom-agent-events"
        config.toolEventTopic = "custom-tool-events"
        config.llmEventTopic = "custom-llm-events"
        
        assertTrue(config.publishAgentEvents)
        assertTrue(config.publishToolEvents)
        assertTrue(config.publishLLMEvents)
        assertEquals("custom-agent-events", config.agentEventTopic)
        assertEquals("custom-tool-events", config.toolEventTopic)
        assertEquals("custom-llm-events", config.llmEventTopic)
    }
}