package ai.koog.agents.features.pubsub.providers.redis

import ai.koog.agents.features.pubsub.message.PubSubMessage
import ai.koog.agents.features.pubsub.message.PubSubStringMessage
import ai.koog.agents.features.pubsub.providers.PubSubException
import ai.koog.agents.features.pubsub.providers.PubSubProvider
import ai.koog.agents.features.pubsub.providers.ReceivedMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import io.lettuce.core.pubsub.RedisPubSubListener
import io.lettuce.core.pubsub.api.reactive.RedisPubSubReactiveCommands
import io.lettuce.core.api.coroutines
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Redis implementation of [PubSubProvider] using Lettuce Redis client.
 *
 * This provider supports Redis pub/sub functionality with:
 * - Publishing messages to channels
 * - Subscribing to single or multiple channels
 * - Pattern-based subscriptions
 * - Connection management and health monitoring
 *
 * Example usage:
 * ```kotlin
 * val provider = RedisPubSubProvider(
 *     redisUri = RedisURI.create("redis://localhost:6379"),
 *     keyPrefix = "agent:",
 *     connectionTimeout = 5000
 * )
 * 
 * // Publish a message
 * val messageId = provider.publish("events", "Hello, World!")
 * 
 * // Subscribe to messages
 * provider.subscribe("events").collect { message ->
 *     println("Received: ${message.content}")
 *     message.acknowledge()
 * }
 * ```
 *
 * @property redisUri The Redis connection URI
 * @property keyPrefix Prefix to add to all channel names (default: "pubsub:")
 * @property connectionTimeout Connection timeout in milliseconds (default: 5000)
 * @property enablePatternSubscription Whether to enable pattern-based subscriptions (default: false)
 */
@OptIn(ExperimentalUuidApi::class, io.lettuce.core.ExperimentalLettuceCoroutinesApi::class)
public class RedisPubSubProvider(
    private val redisUri: RedisURI,
    private val keyPrefix: String = "pubsub:",
    private val connectionTimeout: Long = 5000,
    private val enablePatternSubscription: Boolean = false
) : PubSubProvider {

    private companion object {
        private val logger = KotlinLogging.logger { }
    }
    
    private val redisClient: RedisClient by lazy { RedisClient.create(redisUri) }
    private val pubSubConnection: StatefulRedisPubSubConnection<String, String> by lazy { 
        redisClient.connectPubSub()
    }
    private val pubSubCommands by lazy { pubSubConnection.reactive() }
    
    // Track active subscriptions for cleanup
    private val activeSubscriptions = mutableSetOf<String>()

    override suspend fun publish(message: PubSubMessage): String? {
        return when (message) {
            is PubSubStringMessage -> publish(message.topic, message.content, message.attributes)
            else -> {
                logger.warn { "Unsupported message type for Redis: ${message::class.simpleName}" }
                null
            }
        }
    }

    override suspend fun publish(
        topic: String,
        content: String,
        attributes: Map<String, String>
    ): String? {
        return try {
            val channel = prefixedChannel(topic)
            
            // Redis doesn't support message attributes natively, so we encode them in the message
            val messageContent = if (attributes.isNotEmpty()) {
                buildString {
                    append("ATTRS:")
                    attributes.forEach { (key, value) ->
                        append("$key=$value;")
                    }
                    append("CONTENT:")
                    append(content)
                }
            } else {
                content
            }

            // Use Lettuce coroutines API to publish the message
            val regularConnection = redisClient.connect()
            val coroutineCommands = regularConnection.coroutines()
            
            val subscriberCount = coroutineCommands.publish(channel, messageContent)
            
            logger.debug { "Published message to Redis channel '$channel', reached $subscriberCount subscribers" }
            
            // Generate a unique message ID for tracking (Redis PUBLISH doesn't return one)
            Uuid.random().toString()
            
        } catch (e: Exception) {
            throw PubSubException("publish", topic, "Failed to publish message to Redis channel", e)
        }
    }

    override suspend fun subscribe(topic: String, subscriptionId: String?): Flow<ReceivedMessage> {
        return subscribe(listOf(topic))
    }

    override suspend fun subscribe(topics: List<String>): Flow<ReceivedMessage> {
        return try {
            if (topics.isEmpty()) {
                return emptyFlow()
            }
            
            val channels = topics.map { prefixedChannel(it) }
            logger.info { "Subscribing to Redis channels: ${channels.joinToString()}" }
            
            callbackFlow {
                // Set up the listener for messages
                val listener = object : RedisPubSubListener<String, String> {
                    override fun message(channel: String, message: String) {
                        try {
                            val originalTopic = channel.removePrefix(keyPrefix)
                            val (attributes, content) = parseMessage(message)
                            
                            val receivedMessage = RedisReceivedMessage(
                                messageId = Uuid.random().toString(),
                                topic = originalTopic,
                                content = content,
                                attributes = attributes
                            )
                            
                            if (isActive) {
                                trySend(receivedMessage)
                            }
                        } catch (e: Exception) {
                            logger.error(e) { "Error processing received message from channel: $channel" }
                        }
                    }
                    
                    override fun message(pattern: String, channel: String, message: String) {
                        // Handle pattern subscriptions if enabled
                        if (enablePatternSubscription) {
                            message(channel, message)
                        }
                    }
                    
                    override fun subscribed(channel: String, count: Long) {
                        logger.debug { "Subscribed to Redis channel: $channel (total: $count)" }
                    }
                    
                    override fun unsubscribed(channel: String, count: Long) {
                        logger.debug { "Unsubscribed from Redis channel: $channel (remaining: $count)" }
                    }
                    
                    override fun psubscribed(pattern: String, count: Long) {
                        logger.debug { "Pattern subscribed: $pattern (total: $count)" }
                    }
                    
                    override fun punsubscribed(pattern: String, count: Long) {
                        logger.debug { "Pattern unsubscribed: $pattern (remaining: $count)" }
                    }
                }
                
                // Add the listener to the connection
                pubSubConnection.addListener(listener)
                
                // Subscribe to the channels using reactive API
                pubSubCommands.subscribe(*channels.toTypedArray()).awaitFirstOrNull()
                
                // Track subscriptions for cleanup
                synchronized(activeSubscriptions) {
                    activeSubscriptions.addAll(channels)
                }
                
                awaitClose {
                    try {
                        // Remove listener and unsubscribe (non-suspend operations in awaitClose)
                        pubSubConnection.removeListener(listener)
                        
                        // Use reactive subscribe for cleanup (non-suspend)
                        pubSubCommands.unsubscribe(*channels.toTypedArray()).subscribe()
                        
                        synchronized(activeSubscriptions) {
                            activeSubscriptions.removeAll(channels.toSet())
                        }
                        
                        logger.debug { "Unsubscribed from Redis channels: ${channels.joinToString()}" }
                    } catch (e: Exception) {
                        logger.error(e) { "Error during unsubscribe cleanup" }
                    }
                }
            }
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to subscribe to Redis channels: ${topics.joinToString()}" }
            throw PubSubException("subscribe", topics.joinToString(","), "Failed to subscribe to Redis channels", e)
        }
    }

    override suspend fun unsubscribe(topic: String, subscriptionId: String?) {
        try {
            val channel = prefixedChannel(topic)
            
            // Unsubscribe from the channel
            pubSubCommands.unsubscribe(channel).awaitFirstOrNull()
            
            synchronized(activeSubscriptions) {
                activeSubscriptions.remove(channel)
            }
            
            logger.debug { "Unsubscribed from Redis channel: $channel" }
            
        } catch (e: Exception) {
            throw PubSubException("unsubscribe", topic, "Failed to unsubscribe from Redis channel", e)
        }
    }

    override suspend fun isConnected(): Boolean {
        return try {
            // Test connection by pinging the Redis server
            val regularConnection = redisClient.connect()
            val coroutineCommands = regularConnection.coroutines()
            val result = coroutineCommands.ping()
            result == "PONG"
        } catch (e: Exception) {
            logger.debug(e) { "Redis connection check failed" }
            false
        }
    }

    override suspend fun getHealthInfo(): Map<String, Any> {
        return try {
            val connected = isConnected()
            val info = mutableMapOf<String, Any>(
                "provider" to "redis",
                "connected" to connected,
                "redisUri" to redisUri.toString(),
                "keyPrefix" to keyPrefix,
                "patternSubscription" to enablePatternSubscription,
                "activeSubscriptions" to synchronized(activeSubscriptions) { activeSubscriptions.size }
            )

            if (connected) {
                info["healthy"] = true
            } else {
                info["healthy"] = false
                info["error"] = "Not connected"
            }

            info
        } catch (e: Exception) {
            mapOf(
                "provider" to "redis",
                "connected" to false,
                "healthy" to false,
                "error" to (e.message ?: "Unknown error")
            )
        }
    }

    override fun close() {
        try {
            logger.info { "Closing Redis PubSub connections" }
            
            // Close the pub/sub connection (will be created if not already initialized)
            try {
                pubSubConnection.close()
            } catch (e: Exception) {
                logger.debug(e) { "Error closing pub/sub connection" }
            }
            
            // Shutdown the Redis client (will be created if not already initialized)
            try {
                redisClient.shutdown()
            } catch (e: Exception) {
                logger.debug(e) { "Error shutting down Redis client" }
            }
            
            // Clear tracking data
            synchronized(activeSubscriptions) {
                activeSubscriptions.clear()
            }
            
            logger.info { "Redis PubSub connections closed successfully" }
        } catch (e: Exception) {
            logger.error(e) { "Error closing Redis PubSub connections" }
        }
    }

    //region Private Methods

    private fun prefixedChannel(topic: String): String {
        return if (keyPrefix.isNotEmpty()) "$keyPrefix$topic" else topic
    }
    
    /**
     * Parse a message that may contain encoded attributes.
     * Format: "ATTRS:key1=value1;key2=value2;CONTENT:actual content"
     * Or just: "actual content" if no attributes
     */
    private fun parseMessage(message: String): Pair<Map<String, String>, String> {
        if (!message.startsWith("ATTRS:")) {
            return emptyMap<String, String>() to message
        }
        
        val contentIndex = message.indexOf("CONTENT:")
        if (contentIndex == -1) {
            return emptyMap<String, String>() to message
        }
        
        val attributesString = message.substring(6, contentIndex) // Skip "ATTRS:"
        val content = message.substring(contentIndex + 8) // Skip "CONTENT:"
        
        val attributes = mutableMapOf<String, String>()
        if (attributesString.isNotBlank()) {
            attributesString.split(";").forEach { pair ->
                val equalIndex = pair.indexOf("=")
                if (equalIndex != -1) {
                    val key = pair.substring(0, equalIndex)
                    val value = pair.substring(equalIndex + 1)
                    attributes[key] = value
                }
            }
        }
        
        return attributes to content
    }

    //endregion Private Methods
}

/**
 * Redis-specific ReceivedMessage that handles acknowledgments properly.
 * Since Redis pub/sub doesn't have acknowledgments, this is a no-op implementation.
 */
private class RedisReceivedMessage(
    messageId: String,
    topic: String,
    content: String,
    attributes: Map<String, String>
) : ReceivedMessage(messageId, topic, content, attributes, null) {
    
    override suspend fun acknowledge() {
        // Redis pub/sub doesn't support acknowledgments - this is a no-op
    }
    
    override suspend fun nack() {
        // Redis pub/sub doesn't support negative acknowledgments - this is a no-op
    }
}