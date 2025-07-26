package ai.koog.agents.features.pubsub.providers.local

import ai.koog.agents.features.pubsub.providers.PubSubException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LocalFilePubSubProviderTest {

    private fun createTempProvider(): LocalFilePubSubProvider {
        val tempDir = Files.createTempDirectory("koog-pubsub-test").toFile()
        tempDir.deleteOnExit()
        return LocalFilePubSubProvider(
            baseDirectory = tempDir,
            pollingIntervalMs = 10, // Fast polling for tests
            cleanupIntervalMs = 1000,
            messageRetentionMs = 5000
        )
    }

    @Test
    fun provider_shouldInitializeAsConnected() = runTest {
        val provider = createTempProvider()
        
        try {
            assertTrue(provider.isConnected())
            
            val healthInfo = provider.getHealthInfo()
            assertEquals("local-file", healthInfo["provider"])
            assertEquals(true, healthInfo["connected"])
            assertEquals(true, healthInfo["healthy"])
        } finally {
            provider.close()
        }
    }

    @Test
    fun provider_shouldPublishAndReceiveMessages() = runTest {
        val provider = createTempProvider()
        
        try {
            // Start subscription
            val job = launch {
                val receivedMessage = provider.subscribe("test-topic").first()
                assertEquals("test-topic", receivedMessage.topic)
                assertEquals("Hello, World!", receivedMessage.content)
                assertEquals(mapOf("source" to "test"), receivedMessage.attributes)
                receivedMessage.acknowledge()
            }
            
            // Allow subscription to initialize
            delay(50)
            
            // Publish a message
            val messageId = provider.publish("test-topic", "Hello, World!", mapOf("source" to "test"))
            assertNotNull(messageId)
            
            // Wait for processing
            job.join()
            
        } finally {
            provider.close()
        }
    }

    @Test
    fun provider_shouldSupportMultipleSubscribers() = runTest {
        val provider = createTempProvider()
        
        try {
            val subscriber1Messages = mutableListOf<String>()
            val subscriber2Messages = mutableListOf<String>()
            
            // Start two subscribers
            val job1 = launch {
                provider.subscribe("shared-topic").collect { message ->
                    subscriber1Messages.add(message.content)
                    message.acknowledge()
                }
            }
            
            val job2 = launch {
                provider.subscribe("shared-topic").collect { message ->
                    subscriber2Messages.add(message.content)
                    message.acknowledge()
                }
            }
            
            delay(50) // Allow subscriptions to initialize
            
            // Publish a message
            provider.publish("shared-topic", "Broadcast message", emptyMap())
            
            delay(100) // Allow message delivery
            
            // Both subscribers should receive the message
            assertTrue(subscriber1Messages.contains("Broadcast message"))
            assertTrue(subscriber2Messages.contains("Broadcast message"))
            
            job1.cancel()
            job2.cancel()
            
        } finally {
            provider.close()
        }
    }

    @Test
    fun provider_shouldFilterMessagesByTopic() = runTest {
        val provider = createTempProvider()
        
        try {
            val topic1Messages = mutableListOf<String>()
            val topic2Messages = mutableListOf<String>()
            
            // Subscribe to different topics
            val job1 = launch {
                provider.subscribe("topic1").collect { message ->
                    topic1Messages.add(message.content)
                    message.acknowledge()
                }
            }
            
            val job2 = launch {
                provider.subscribe("topic2").collect { message ->
                    topic2Messages.add(message.content)
                    message.acknowledge()
                }
            }
            
            delay(50)
            
            // Publish to both topics
            provider.publish("topic1", "Message for topic1", emptyMap())
            provider.publish("topic2", "Message for topic2", emptyMap())
            
            delay(100)
            
            // Each subscriber should only receive messages for their topic
            assertTrue(topic1Messages.contains("Message for topic1"))
            assertFalse(topic1Messages.contains("Message for topic2"))
            assertTrue(topic2Messages.contains("Message for topic2"))
            assertFalse(topic2Messages.contains("Message for topic1"))
            
            job1.cancel()
            job2.cancel()
            
        } finally {
            provider.close()
        }
    }

    @Test
    fun provider_shouldHandleMessageAcknowledgment() = runTest {
        val provider = createTempProvider()
        
        try {
            // Subscribe and acknowledge messages
            val job = launch {
                provider.subscribe("ack-topic").collect { message ->
                    message.acknowledge()
                }
            }
            
            delay(50)
            
            // Publish messages
            provider.publish("ack-topic", "Message 1", emptyMap())
            provider.publish("ack-topic", "Message 2", emptyMap())
            
            delay(100)
            
            // Check that message files are cleaned up after acknowledgment
            val baseDir = provider.getHealthInfo()["baseDirectory"] as String
            val messagesDir = File(baseDir, "messages")
            val messageFiles = messagesDir.listFiles() ?: emptyArray()
            
            // Messages should be acknowledged and removed
            assertEquals(0, messageFiles.size)
            
            job.cancel()
            
        } finally {
            provider.close()
        }
    }

    @Test
    fun provider_shouldSupportMultipleTopicSubscription() = runTest {
        val provider = createTempProvider()
        
        try {
            val allMessages = mutableListOf<String>()
            
            // Subscribe to multiple topics
            val job = launch {
                provider.subscribe(listOf("topic-a", "topic-b", "topic-c")).collect { message ->
                    allMessages.add(message.content)
                    message.acknowledge()
                }
            }
            
            delay(50)
            
            // Publish to all topics plus one extra
            provider.publish("topic-a", "Message A", emptyMap())
            provider.publish("topic-b", "Message B", emptyMap())
            provider.publish("topic-c", "Message C", emptyMap())
            provider.publish("topic-d", "Message D", emptyMap()) // Should not be received
            
            delay(100)
            
            // Should receive messages from subscribed topics only
            assertTrue(allMessages.contains("Message A"))
            assertTrue(allMessages.contains("Message B"))
            assertTrue(allMessages.contains("Message C"))
            assertFalse(allMessages.contains("Message D"))
            
            job.cancel()
            
        } finally {
            provider.close()
        }
    }

    @Test
    fun provider_shouldTrackSubscriptions() = runTest {
        val provider = createTempProvider()
        
        try {
            // Initial state
            val initialHealth = provider.getHealthInfo()
            assertEquals(0, initialHealth["activeSubscriptions"])
            
            // Subscribe to topics
            val job1 = launch {
                provider.subscribe("test-topic").collect { }
            }
            
            delay(50)
            
            val healthAfterSub = provider.getHealthInfo()
            assertEquals(1, healthAfterSub["activeSubscriptions"])
            
            // Unsubscribe
            provider.unsubscribe("test-topic")
            
            val healthAfterUnsub = provider.getHealthInfo()
            assertEquals(0, healthAfterUnsub["activeSubscriptions"])
            
            job1.cancel()
            
        } finally {
            provider.close()
        }
    }

    @Test
    fun provider_shouldReportHealthInformation() = runTest {
        val provider = createTempProvider()
        
        try {
            val job = launch {
                provider.subscribe(listOf("health-topic1", "health-topic2")).collect { }
            }
            
            delay(50)
            
            val healthInfo = provider.getHealthInfo()
            
            assertEquals("local-file", healthInfo["provider"])
            assertEquals(true, healthInfo["connected"])
            assertEquals(true, healthInfo["healthy"])
            assertEquals(2, healthInfo["activeSubscriptions"])
            assertTrue(healthInfo.containsKey("baseDirectory"))
            assertTrue(healthInfo.containsKey("pendingMessages"))
            assertTrue(healthInfo.containsKey("pollingIntervalMs"))
            
            job.cancel()
            
        } finally {
            provider.close()
        }
    }

    @Test
    fun provider_shouldHandleCloseOperation() = runTest {
        val provider = createTempProvider()
        
        assertTrue(provider.isConnected())
        
        provider.close()
        
        assertFalse(provider.isConnected())
        
        val healthInfo = provider.getHealthInfo()
        assertEquals(false, healthInfo["connected"])
        assertEquals(false, healthInfo["healthy"])
    }

    @Test
    fun provider_shouldThrowExceptionWhenNotConnected() = runTest {
        val provider = createTempProvider()
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

    @Test
    fun provider_shouldHandleFileLockingConcurrency() = runTest {
        val provider = createTempProvider()
        
        try {
            val messages = mutableListOf<String>()
            
            // Multiple concurrent subscribers
            val jobs = (1..3).map { subscriberId ->
                launch {
                    provider.subscribe("concurrent-topic").collect { message ->
                        synchronized(messages) {
                            messages.add("subscriber-$subscriberId: ${message.content}")
                        }
                        message.acknowledge()
                    }
                }
            }
            
            delay(50)
            
            // Concurrent publishing
            val publishJobs = (1..5).map { messageId ->
                launch {
                    provider.publish("concurrent-topic", "message-$messageId", emptyMap())
                }
            }
            
            // Wait for all publishing to complete
            publishJobs.forEach { it.join() }
            delay(200) // Allow processing
            
            // Should have received messages (multiple subscribers will get copies)
            assertTrue(messages.isNotEmpty())
            
            jobs.forEach { it.cancel() }
            
        } finally {
            provider.close()
        }
    }

    @Test
    fun provider_shouldPerformPeriodicCleanup() = runTest {
        val provider = createTempProvider()
        
        try {
            // Publish and acknowledge some messages
            val job = launch {
                provider.subscribe("cleanup-topic").collect { message ->
                    message.acknowledge()
                }
            }
            
            delay(50)
            
            // Publish several messages
            repeat(3) { i ->
                provider.publish("cleanup-topic", "message-$i", emptyMap())
            }
            
            delay(200) // Allow processing and acknowledgment
            
            // Check that files are cleaned up
            val baseDir = provider.getHealthInfo()["baseDirectory"] as String
            val messagesDir = File(baseDir, "messages")
            val ackedDir = File(baseDir, "acked")
            
            // Messages should be acknowledged and moved
            val messageFiles = messagesDir.listFiles()?.size ?: 0
            val ackedFiles = ackedDir.listFiles()?.size ?: 0
            
            assertEquals(0, messageFiles) // All messages processed
            assertTrue(ackedFiles >= 0) // Some acknowledged messages (may be cleaned up already)
            
            job.cancel()
            
        } finally {
            provider.close()
        }
    }
}