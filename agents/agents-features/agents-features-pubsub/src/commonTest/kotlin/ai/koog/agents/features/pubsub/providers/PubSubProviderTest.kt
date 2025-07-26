package ai.koog.agents.features.pubsub.providers

import ai.koog.agents.features.pubsub.message.PubSubStringMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PubSubProviderTest {

    @Test
    fun noPubSubProvider_shouldReturnNullForPublish() = runTest {
        val provider = NoPubSubProvider()
        
        val message = PubSubStringMessage(
            topic = "test-topic",
            content = "test message",
            attributes = mapOf("key" to "value")
        )
        
        val messageId = provider.publish(message)
        assertEquals(null, messageId)
    }

    @Test
    fun noPubSubProvider_shouldReturnEmptyFlowForSubscribe() = runTest {
        val provider = NoPubSubProvider()
        
        val messageFlow = provider.subscribe("test-topic")
        
        // Flow should be empty - no messages received
        val messages = mutableListOf<ReceivedMessage>()
        try {
            messageFlow.collect { 
                messages.add(it) 
            }
        } catch (e: Exception) {
            // Expected - empty flow should complete or throw
        }
        
        assertTrue(messages.isEmpty())
    }

    @Test
    fun noPubSubProvider_shouldReturnDisconnectedStatus() = runTest {
        val provider = NoPubSubProvider()
        
        val isConnected = provider.isConnected()
        assertEquals(false, isConnected)
    }

    @Test
    fun noPubSubProvider_shouldReturnHealthInfo() = runTest {
        val provider = NoPubSubProvider()
        
        val healthInfo = provider.getHealthInfo()
        
        assertEquals("no-op", healthInfo["provider"])
        assertEquals(false, healthInfo["connected"])
    }

    @Test
    fun receivedMessage_shouldAllowAcknowledgment() = runTest {
        val message = ReceivedMessage(
            messageId = "test-id",
            topic = "test-topic",
            content = "test content",
            attributes = mapOf("attr" to "value"),
            acknowledgmentToken = "test-token"
        )
        
        // Should not throw exception
        message.acknowledge()
        message.nack()
    }

    @Test
    fun pubSubException_shouldContainAllDetails() {
        val operation = "publish"
        val topic = "test-topic"
        val message = "Test error message"
        val cause = RuntimeException("Root cause")
        
        val exception = PubSubException(operation, topic, message, cause)
        
        assertEquals(operation, exception.operation)
        assertEquals(topic, exception.topic)
        assertEquals(message, exception.message)
        assertEquals(cause, exception.cause)
    }
}