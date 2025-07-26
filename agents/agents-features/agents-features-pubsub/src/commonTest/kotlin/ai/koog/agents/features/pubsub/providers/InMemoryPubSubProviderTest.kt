package ai.koog.agents.features.pubsub.providers

import ai.koog.agents.features.pubsub.message.PubSubMessage
import ai.koog.agents.features.pubsub.message.PubSubStringMessage
import ai.koog.agents.features.common.message.FeatureMessage
import kotlinx.datetime.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InMemoryPubSubProviderTest {

    @Test
    fun provider_shouldInitializeAsConnected() = runTest {
        val provider = InMemoryPubSubProvider()
        
        assertTrue(provider.isConnected())
        
        val healthInfo = provider.getHealthInfo()
        assertEquals("in-memory", healthInfo["provider"])
        assertEquals(true, healthInfo["connected"])
        assertEquals(true, healthInfo["healthy"])
    }

    @Test
    fun provider_shouldPublishAndReceiveMessages() = runTest {
        val provider = InMemoryPubSubProvider()
        
        // Start subscription
        val messagesReceived = mutableListOf<ReceivedMessage>()
        val job = launch {
            provider.subscribe("test-topic").collect { message ->
                messagesReceived.add(message)
            }
        }
        
        // Allow subscription to initialize
        delay(10)
        
        // Publish a message
        val messageId = provider.publish("test-topic", "Hello, World!", mapOf("source" to "test"))
        assertNotNull(messageId)
        
        // Allow message to be delivered
        delay(10)
        
        // Verify message was received
        assertEquals(1, messagesReceived.size)
        val receivedMessage = messagesReceived[0]
        assertEquals(messageId, receivedMessage.messageId)
        assertEquals("test-topic", receivedMessage.topic)
        assertEquals("Hello, World!", receivedMessage.content)
        assertEquals(mapOf("source" to "test"), receivedMessage.attributes)
        
        job.cancel()
    }

    @Test
    fun provider_shouldSupportMultipleSubscribers() = runTest {
        val provider = InMemoryPubSubProvider()
        
        val subscriber1Messages = mutableListOf<ReceivedMessage>()
        val subscriber2Messages = mutableListOf<ReceivedMessage>()
        
        // Start two subscribers
        val job1 = launch {
            provider.subscribe("shared-topic").collect { message ->
                subscriber1Messages.add(message)
            }
        }
        
        val job2 = launch {
            provider.subscribe("shared-topic").collect { message ->
                subscriber2Messages.add(message)
            }
        }
        
        delay(10) // Allow subscriptions to initialize
        
        // Publish a message
        provider.publish("shared-topic", "Broadcast message", emptyMap())
        
        delay(10) // Allow message delivery
        
        // Both subscribers should receive the message
        assertEquals(1, subscriber1Messages.size)
        assertEquals(1, subscriber2Messages.size)
        assertEquals("Broadcast message", subscriber1Messages[0].content)
        assertEquals("Broadcast message", subscriber2Messages[0].content)
        
        job1.cancel()
        job2.cancel()
    }

    @Test
    fun provider_shouldFilterMessagesByTopic() = runTest {
        val provider = InMemoryPubSubProvider()
        
        val topic1Messages = mutableListOf<ReceivedMessage>()
        val topic2Messages = mutableListOf<ReceivedMessage>()
        
        // Subscribe to different topics
        val job1 = launch {
            provider.subscribe("topic1").collect { message ->
                topic1Messages.add(message)
            }
        }
        
        val job2 = launch {
            provider.subscribe("topic2").collect { message ->
                topic2Messages.add(message)
            }
        }
        
        delay(10)
        
        // Publish to both topics
        provider.publish("topic1", "Message for topic1", emptyMap())
        provider.publish("topic2", "Message for topic2", emptyMap())
        
        delay(10)
        
        // Each subscriber should only receive messages for their topic
        assertEquals(1, topic1Messages.size)
        assertEquals(1, topic2Messages.size)
        assertEquals("Message for topic1", topic1Messages[0].content)
        assertEquals("Message for topic2", topic2Messages[0].content)
        
        job1.cancel()
        job2.cancel()
    }

    @Test
    fun provider_shouldSupportMultipleTopicSubscription() = runTest {
        val provider = InMemoryPubSubProvider()
        
        val allMessages = mutableListOf<ReceivedMessage>()
        
        // Subscribe to multiple topics
        val job = launch {
            provider.subscribe(listOf("topic-a", "topic-b", "topic-c")).collect { message ->
                allMessages.add(message)
            }
        }
        
        delay(10)
        
        // Publish to all topics
        provider.publish("topic-a", "Message A", emptyMap())
        provider.publish("topic-b", "Message B", emptyMap())
        provider.publish("topic-c", "Message C", emptyMap())
        provider.publish("topic-d", "Message D", emptyMap()) // Should not be received
        
        delay(10)
        
        // Should receive messages from subscribed topics only
        assertEquals(3, allMessages.size)
        assertTrue(allMessages.any { it.content == "Message A" })
        assertTrue(allMessages.any { it.content == "Message B" })
        assertTrue(allMessages.any { it.content == "Message C" })
        assertFalse(allMessages.any { it.content == "Message D" })
        
        job.cancel()
    }

    @Test
    fun provider_shouldHandleMessageAcknowledgment() = runTest {
        val provider = InMemoryPubSubProvider()
        
        // Clear any existing acknowledged messages
        provider.clearAcknowledgedMessages()
        
        // Subscribe and acknowledge messages
        val job = launch {
            provider.subscribe("ack-topic").collect { message ->
                message.acknowledge()
            }
        }
        
        delay(10)
        
        // Publish messages
        provider.publish("ack-topic", "Message 1", emptyMap())
        provider.publish("ack-topic", "Message 2", emptyMap())
        
        delay(10)
        
        // Check health info for acknowledged messages
        val healthInfo = provider.getHealthInfo()
        assertEquals(2, healthInfo["acknowledgedMessages"])
        
        job.cancel()
    }

    @Test
    fun provider_shouldHandleMessageNegativeAcknowledgment() = runTest {
        val provider = InMemoryPubSubProvider()
        
        provider.clearAcknowledgedMessages()
        
        val job = launch {
            provider.subscribe("nack-topic").collect { message ->
                message.nack() // Negative acknowledgment
            }
        }
        
        delay(10)
        
        provider.publish("nack-topic", "Message to nack", emptyMap())
        
        delay(10)
        
        // Nacked messages should not be in acknowledged set
        val healthInfo = provider.getHealthInfo()
        assertEquals(0, healthInfo["acknowledgedMessages"])
        
        job.cancel()
    }

    @Test
    fun provider_shouldTrackSubscriptions() = runTest {
        val provider = InMemoryPubSubProvider()
        
        assertEquals(0, provider.getSubscriptionCount("test-topic"))
        
        // Subscribe multiple times to the same topic
        val job1 = launch {
            provider.subscribe("test-topic").collect { }
        }
        
        delay(10)
        assertEquals(1, provider.getSubscriptionCount("test-topic"))
        
        val job2 = launch {
            provider.subscribe("test-topic").collect { }
        }
        
        delay(10)
        assertEquals(2, provider.getSubscriptionCount("test-topic"))
        
        // Unsubscribe
        provider.unsubscribe("test-topic")
        assertEquals(1, provider.getSubscriptionCount("test-topic"))
        
        provider.unsubscribe("test-topic")
        assertEquals(0, provider.getSubscriptionCount("test-topic"))
        
        job1.cancel()
        job2.cancel()
    }

    @Test
    fun provider_shouldReportHealthInformation() = runTest {
        val provider = InMemoryPubSubProvider()
        
        val job1 = launch {
            provider.subscribe("health-topic1").collect { }
        }
        
        val job2 = launch {
            provider.subscribe("health-topic2").collect { }
        }
        
        delay(10)
        
        val healthInfo = provider.getHealthInfo()
        
        assertEquals("in-memory", healthInfo["provider"])
        assertEquals(true, healthInfo["connected"])
        assertEquals(true, healthInfo["healthy"])
        assertEquals(2, healthInfo["totalActiveSubscriptions"])
        assertEquals(256, healthInfo["bufferCapacity"])
        assertEquals(0, healthInfo["replayCache"])
        
        val activeSubscriptions = healthInfo["activeSubscriptions"] as Map<*, *>
        assertEquals(1, activeSubscriptions["health-topic1"])
        assertEquals(1, activeSubscriptions["health-topic2"])
        
        job1.cancel()
        job2.cancel()
    }

    @Test
    fun provider_shouldHandlePubSubStringMessage() = runTest {
        val provider = InMemoryPubSubProvider()
        
        val message = PubSubStringMessage(
            topic = "string-topic",
            content = "String message content",
            attributes = mapOf("type" to "string")
        )
        
        // Start subscription first
        val job = launch {
            val receivedMessage = provider.subscribe("string-topic").first()
            assertEquals("String message content", receivedMessage.content)
            assertEquals(mapOf("type" to "string"), receivedMessage.attributes)
        }
        
        delay(10) // Allow subscription to initialize
        
        val messageId = provider.publish(message)
        assertNotNull(messageId)
        
        job.join() // Wait for subscription to complete
    }

    @Test
    fun provider_shouldReturnNullForUnsupportedMessageTypes() = runTest {
        val provider = InMemoryPubSubProvider()
        
        val unsupportedMessage = object : PubSubMessage {
            override val topic: String = "test"
            override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
            override val messageType: FeatureMessage.Type = FeatureMessage.Type.Message
        }
        
        val messageId = provider.publish(unsupportedMessage)
        assertEquals(null, messageId)
    }

    @Test
    fun provider_shouldHandleEmptyTopicsList() = runTest {
        val provider = InMemoryPubSubProvider()
        
        val messages = provider.subscribe(emptyList()).toList()
        assertEquals(0, messages.size)
    }

    @Test
    fun provider_shouldHandleCloseOperation() = runTest {
        val provider = InMemoryPubSubProvider()
        
        assertTrue(provider.isConnected())
        
        provider.close()
        
        assertFalse(provider.isConnected())
        
        val healthInfo = provider.getHealthInfo()
        assertEquals(false, healthInfo["connected"])
        assertEquals(false, healthInfo["healthy"])
    }

    @Test
    fun provider_shouldThrowExceptionWhenNotConnected() = runTest {
        val provider = InMemoryPubSubProvider()
        provider.close()
        
        // Publishing when not connected should throw exception
        try {
            provider.publish("test-topic", "test message", emptyMap())
            assertTrue(false, "Expected PubSubException")
        } catch (e: PubSubException) {
            assertEquals("publish", e.operation)
            assertEquals("test-topic", e.topic)
        }
        
        // Subscribing when not connected should throw exception
        try {
            provider.subscribe("test-topic")
            assertTrue(false, "Expected PubSubException")
        } catch (e: PubSubException) {
            assertEquals("subscribe", e.operation)
        }
    }
}