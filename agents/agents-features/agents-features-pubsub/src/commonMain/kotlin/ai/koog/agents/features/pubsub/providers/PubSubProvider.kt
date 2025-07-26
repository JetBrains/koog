package ai.koog.agents.features.pubsub.providers

import ai.koog.agents.features.pubsub.message.PubSubMessage
import kotlinx.coroutines.flow.Flow

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
public data class ReceivedMessage(
    val messageId: String,
    val topic: String,
    val content: String,
    val attributes: Map<String, String> = emptyMap(),
    val acknowledgmentToken: Any? = null
) {
    /**
     * Acknowledges this message, indicating successful processing.
     * This is typically used to remove the message from the subscription queue.
     */
    public suspend fun acknowledge() {
        // Implementation will be provider-specific
        // This is a placeholder - actual implementation will be in concrete providers
    }
    
    /**
     * Negatively acknowledges this message, indicating processing failure.
     * This typically causes the message to be redelivered after a delay.
     */
    public suspend fun nack() {
        // Implementation will be provider-specific
        // This is a placeholder - actual implementation will be in concrete providers  
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