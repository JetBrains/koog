package ai.koog.agents.features.pubsub.providers

import ai.koog.agents.features.pubsub.message.PubSubMessage
import ai.koog.agents.features.pubsub.message.PubSubStringMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Abstract interface for PubSub providers that can publish and subscribe to messages.
 *
 * This interface provides a provider-agnostic API for pub/sub operations, allowing
 * different implementations (Redis, GCP PubSub, etc.) to be used interchangeably.
 *
 * Implementations should handle:
 * - Connection management and authentication
 * - Topic/subscription lifecycle management  
 * - Message serialization/deserialization
 * - Error handling and retry logic
 * - Graceful shutdown and resource cleanup
 */
public interface PubSubProvider : AutoCloseable {
    
    /**
     * Publishes a message to the specified topic.
     *
     * @param message The message to publish
     * @return The message ID assigned by the provider, or null if not supported
     * @throws PubSubException if the publish operation fails
     */
    public suspend fun publish(message: PubSubMessage): String?
    
    /**
     * Publishes a simple text message to the specified topic.
     *
     * This is a convenience method that wraps the content in a [PubSubStringMessage].
     *
     * @param topic The topic to publish to
     * @param content The message content
     * @param attributes Optional message attributes/headers
     * @return The message ID assigned by the provider, or null if not supported
     * @throws PubSubException if the publish operation fails
     */
    public suspend fun publish(
        topic: String, 
        content: String, 
        attributes: Map<String, String> = emptyMap()
    ): String?
    
    /**
     * Subscribes to messages from the specified topic.
     *
     * @param topic The topic to subscribe to
     * @param subscriptionId Optional subscription identifier (provider-specific)
     * @return A flow of received messages
     * @throws PubSubException if the subscription fails
     */
    public suspend fun subscribe(topic: String, subscriptionId: String? = null): Flow<ReceivedMessage>
    
    /**
     * Subscribes to multiple topics simultaneously.
     *
     * @param topics The topics to subscribe to
     * @return A flow of received messages from all topics
     * @throws PubSubException if any subscription fails
     */
    public suspend fun subscribe(topics: List<String>): Flow<ReceivedMessage>
    
    /**
     * Unsubscribes from the specified topic.
     *
     * @param topic The topic to unsubscribe from
     * @param subscriptionId Optional subscription identifier
     * @throws PubSubException if the unsubscribe operation fails
     */
    public suspend fun unsubscribe(topic: String, subscriptionId: String? = null)
    
    /**
     * Checks if the provider is currently connected and ready for operations.
     *
     * @return true if connected and ready, false otherwise
     */
    public suspend fun isConnected(): Boolean
    
    /**
     * Gets provider-specific health information.
     *
     * @return A map of health metrics and status information
     */
    public suspend fun getHealthInfo(): Map<String, Any>
}

/**
 * Represents a message received from a PubSub subscription.
 *
 * @property messageId The unique ID assigned by the provider
 * @property topic The topic this message was received from
 * @property content The message content
 * @property attributes Message attributes/headers
 * @property acknowledgmentToken Provider-specific token for message acknowledgment
 */
public open class ReceivedMessage(
    public val messageId: String,
    public val topic: String,
    public val content: String,
    public val attributes: Map<String, String> = emptyMap(),
    public val acknowledgmentToken: Any? = null
) {
    /**
     * Acknowledges this message, indicating successful processing.
     * This is typically used to remove the message from the subscription queue.
     */
    public open suspend fun acknowledge() {
        // The acknowledgmentToken should be handled by the provider that created this message
        // Each provider can cast the token to its specific type and handle acknowledgment
        // This is intentionally a no-op in the base class
    }
    
    /**
     * Negatively acknowledges this message, indicating processing failure.
     * This typically causes the message to be redelivered after a delay.
     */
    public open suspend fun nack() {
        // The acknowledgmentToken should be handled by the provider that created this message
        // Each provider can cast the token to its specific type and handle negative acknowledgment
        // This is intentionally a no-op in the base class
    }
}

/**
 * Exception thrown by PubSub operations.
 *
 * @property operation The operation that failed
 * @property topic The topic involved (if applicable)
 * @property cause The underlying cause
 */
public class PubSubException(
    public val operation: String,
    public val topic: String? = null,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * No-op implementation of [PubSubProvider] that discards all messages.
 *
 * This is useful for testing or when PubSub functionality is disabled.
 */
public class NoPubSubProvider : PubSubProvider {
    
    override suspend fun publish(message: PubSubMessage): String? = null
    
    override suspend fun publish(
        topic: String, 
        content: String, 
        attributes: Map<String, String>
    ): String? = null
    
    override suspend fun subscribe(topic: String, subscriptionId: String?): Flow<ReceivedMessage> {
        return kotlinx.coroutines.flow.emptyFlow()
    }
    
    override suspend fun subscribe(topics: List<String>): Flow<ReceivedMessage> {
        return kotlinx.coroutines.flow.emptyFlow()
    }
    
    override suspend fun unsubscribe(topic: String, subscriptionId: String?) {
        // No-op
    }
    
    override suspend fun isConnected(): Boolean = false
    
    override suspend fun getHealthInfo(): Map<String, Any> = mapOf(
        "provider" to "no-op",
        "connected" to false
    )
    
    override fun close() {
        // No-op
    }
}

/**
 * In-memory implementation of [PubSubProvider] for local development and testing.
 *
 * This provider implements full pub/sub functionality within a single JVM process:
 * - Messages are stored in memory and delivered to all active subscribers
 * - Supports multiple subscribers per topic
 * - Messages are acknowledged/nacked through callback mechanisms
 * - Provides real pub/sub behavior without external dependencies
 * - Thread-safe operations using coroutines and mutex synchronization
 *
 * Ideal for:
 * - Local development environments
 * - Unit and integration testing
 * - CI/CD environments without external services
 * - Development setups where Redis/GCP is not available
 *
 * Example usage:
 * ```kotlin
 * val provider = InMemoryPubSubProvider()
 * 
 * // Publish a message
 * val messageId = provider.publish("events", "Hello, World!", mapOf("source" to "agent"))
 * 
 * // Subscribe to messages
 * provider.subscribe("events").collect { message ->
 *     println("Received: ${message.content}")
 *     message.acknowledge() // Important for proper cleanup
 * }
 * ```
 *
 * Note: Messages are not persisted across process restarts since they're stored in memory.
 */
@OptIn(ExperimentalUuidApi::class)
public class InMemoryPubSubProvider : PubSubProvider {
    
    private companion object {
        // Use a replay cache to ensure new subscribers receive a clean state
        private const val REPLAY_CACHE = 0
        private const val EXTRA_BUFFER_CAPACITY = 256
    }
    
    // Global message flow shared across all topics
    private val messageFlow = MutableSharedFlow<InternalMessage>(
        replay = REPLAY_CACHE,
        extraBufferCapacity = EXTRA_BUFFER_CAPACITY
    )
    
    // Track active subscriptions for health info
    private val activeSubscriptions = mutableMapOf<String, Int>()
    private val subscriptionMutex = Mutex()
    
    // Track acknowledged messages for potential cleanup
    private val acknowledgedMessages = mutableSetOf<String>()
    private val ackMutex = Mutex()
    
    private var isConnected = true
    
    override suspend fun publish(message: PubSubMessage): String? {
        return when (message) {
            is PubSubStringMessage -> publish(message.topic, message.content, message.attributes)
            else -> {
                // Unsupported message type for in-memory provider
                null
            }
        }
    }
    
    override suspend fun publish(
        topic: String,
        content: String,
        attributes: Map<String, String>
    ): String? {
        if (!isConnected) {
            throw PubSubException("publish", topic, "InMemory provider is not connected")
        }
        
        val messageId = Uuid.random().toString()
        val internalMessage = InternalMessage(
            messageId = messageId,
            topic = topic,
            content = content,
            attributes = attributes
        )
        
        // Emit the message to all subscribers
        val emitted = messageFlow.tryEmit(internalMessage)
        
        return if (emitted) messageId else null
    }
    
    override suspend fun subscribe(topic: String, subscriptionId: String?): Flow<ReceivedMessage> {
        return subscribe(listOf(topic))
    }
    
    override suspend fun subscribe(topics: List<String>): Flow<ReceivedMessage> {
        if (!isConnected) {
            throw PubSubException("subscribe", topics.joinToString(","), "InMemory provider is not connected")
        }
        
        if (topics.isEmpty()) {
            return kotlinx.coroutines.flow.emptyFlow()
        }
        
        // Update subscription tracking
        subscriptionMutex.withLock {
            topics.forEach { topic ->
                activeSubscriptions[topic] = (activeSubscriptions[topic] ?: 0) + 1
            }
        }
        
        return messageFlow
            .asSharedFlow()
            .filter { internalMessage -> topics.contains(internalMessage.topic) }
            .map { internalMessage ->
                InMemoryReceivedMessage(
                    messageId = internalMessage.messageId,
                    topic = internalMessage.topic,
                    content = internalMessage.content,
                    attributes = internalMessage.attributes,
                    acknowledgeCallback = { messageId ->
                        ackMutex.withLock {
                            acknowledgedMessages.add(messageId)
                        }
                    },
                    nackCallback = { messageId ->
                        ackMutex.withLock {
                            acknowledgedMessages.remove(messageId)
                        }
                        // In a real system, nacked messages might be redelivered
                        // For in-memory implementation, we just track the nack
                    }
                )
            }
    }
    
    override suspend fun unsubscribe(topic: String, subscriptionId: String?) {
        subscriptionMutex.withLock {
            val currentCount = activeSubscriptions[topic] ?: 0
            if (currentCount > 1) {
                activeSubscriptions[topic] = currentCount - 1
            } else {
                activeSubscriptions.remove(topic)
            }
        }
    }
    
    override suspend fun isConnected(): Boolean = isConnected
    
    override suspend fun getHealthInfo(): Map<String, Any> {
        val subscriptionInfo = subscriptionMutex.withLock {
            activeSubscriptions.toMap()
        }
        
        val acknowledgedCount = ackMutex.withLock {
            acknowledgedMessages.size
        }
        
        return mapOf(
            "provider" to "in-memory",
            "connected" to isConnected,
            "healthy" to isConnected,
            "activeSubscriptions" to subscriptionInfo,
            "totalActiveSubscriptions" to subscriptionInfo.values.sum(),
            "acknowledgedMessages" to acknowledgedCount,
            "bufferCapacity" to EXTRA_BUFFER_CAPACITY,
            "replayCache" to REPLAY_CACHE
        )
    }
    
    override fun close() {
        isConnected = false
        
        // Clear all tracking data
        activeSubscriptions.clear()
        acknowledgedMessages.clear()
        
        // Note: We don't close the messageFlow as other subscribers might still be active
        // In a real implementation, you might want to implement reference counting
    }
    
    /**
     * Additional method for testing - clears all acknowledged message history
     */
    public suspend fun clearAcknowledgedMessages() {
        ackMutex.withLock {
            acknowledgedMessages.clear()
        }
    }
    
    /**
     * Additional method for testing - gets current subscription count for a topic
     */
    public suspend fun getSubscriptionCount(topic: String): Int {
        return subscriptionMutex.withLock {
            activeSubscriptions[topic] ?: 0
        }
    }
}

/**
 * Internal message representation for the in-memory provider.
 */
private data class InternalMessage(
    val messageId: String,
    val topic: String,
    val content: String,
    val attributes: Map<String, String>
)

/**
 * In-memory specific ReceivedMessage that handles acknowledgments through callbacks.
 */
private class InMemoryReceivedMessage(
    messageId: String,
    topic: String,
    content: String,
    attributes: Map<String, String>,
    private val acknowledgeCallback: suspend (String) -> Unit,
    private val nackCallback: suspend (String) -> Unit
) : ReceivedMessage(messageId, topic, content, attributes, null) {
    
    override suspend fun acknowledge() {
        acknowledgeCallback(messageId)
    }
    
    override suspend fun nack() {
        nackCallback(messageId)
    }
}