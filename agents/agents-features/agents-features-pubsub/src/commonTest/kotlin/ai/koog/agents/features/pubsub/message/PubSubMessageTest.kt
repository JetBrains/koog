package ai.koog.agents.features.pubsub.message

import kotlin.test.Test
import kotlin.test.assertEquals

class PubSubMessageTest {

    @Test
    fun pubSubStringMessage_shouldContainAllProperties() {
        val topic = "test-topic"
        val content = "test content"
        val attributes = mapOf("key1" to "value1", "key2" to "value2")
        
        val message = PubSubStringMessage(
            topic = topic,
            content = content,
            attributes = attributes
        )
        
        assertEquals(topic, message.topic)
        assertEquals(content, message.content)
        assertEquals(attributes, message.attributes)
    }

    @Test
    fun pubSubStringMessage_shouldHandleEmptyAttributes() {
        val message = PubSubStringMessage(
            topic = "test-topic",
            content = "test content",
            attributes = emptyMap()
        )
        
        assertEquals(emptyMap(), message.attributes)
    }

    @Test
    fun messagePublishedEvent_shouldContainEventDetails() {
        val eventId = "event-123"
        val topic = "test-topic"
        val messageId = "msg-456"
        
        val event = MessagePublishedEvent(
            eventId = eventId,
            topic = topic,
            messageId = messageId
        )
        
        assertEquals(eventId, event.eventId)
        assertEquals(topic, event.topic)
        assertEquals(messageId, event.messageId)
    }

    @Test
    fun messageReceivedEvent_shouldContainAllData() {
        val eventId = "event-123"
        val topic = "test-topic"
        val messageId = "msg-456"
        val content = "test content"
        val attributes = mapOf("source" to "agent")
        
        val event = MessageReceivedEvent(
            eventId = eventId,
            topic = topic,
            messageId = messageId,
            content = content,
            attributes = attributes
        )
        
        assertEquals(eventId, event.eventId)
        assertEquals(topic, event.topic)
        assertEquals(messageId, event.messageId)
        assertEquals(content, event.content)
        assertEquals(attributes, event.attributes)
    }

    @Test
    fun subscriptionCreatedEvent_shouldContainSubscriptionInfo() {
        val eventId = "event-123"
        val topic = "topic1"
        val subscriptionId = "sub-456"
        
        val event = SubscriptionCreatedEvent(
            eventId = eventId,
            topic = topic,
            subscriptionId = subscriptionId
        )
        
        assertEquals(eventId, event.eventId)
        assertEquals(topic, event.topic)
        assertEquals(subscriptionId, event.subscriptionId)
    }

    @Test
    fun pubSubErrorEvent_shouldContainErrorDetails() {
        val eventId = "event-123"
        val topic = "test-topic"
        val operation = "publish"
        val error = "Connection failed"
        
        val event = PubSubErrorEvent(
            eventId = eventId,
            topic = topic,
            operation = operation,
            error = error
        )
        
        assertEquals(eventId, event.eventId)
        assertEquals(topic, event.topic)
        assertEquals(operation, event.operation)
        assertEquals(error, event.error)
    }
}