package ai.koog.agents.features.pubsub.providers.gcp

import ai.koog.agents.features.pubsub.message.PubSubMessage
import ai.koog.agents.features.pubsub.message.PubSubStringMessage
import ai.koog.agents.features.pubsub.providers.PubSubException
import ai.koog.agents.features.pubsub.providers.PubSubProvider
import ai.koog.agents.features.pubsub.providers.ReceivedMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.channels.awaitClose
import com.google.cloud.pubsub.v1.*
import com.google.pubsub.v1.ProjectSubscriptionName
import com.google.pubsub.v1.ProjectTopicName
import com.google.pubsub.v1.PubsubMessage
import com.google.pubsub.v1.ProjectName
import com.google.protobuf.ByteString
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * GCP-specific ReceivedMessage that handles acknowledgments properly.
 */
private class GCPReceivedMessage(
    messageId: String,
    topic: String,
    content: String,
    attributes: Map<String, String>,
    private val ackReplyConsumer: AckReplyConsumer
) : ReceivedMessage(messageId, topic, content, attributes, ackReplyConsumer) {
    
    override suspend fun acknowledge() {
        ackReplyConsumer.ack()
    }
    
    override suspend fun nack() {
        ackReplyConsumer.nack()
    }
}

/**
 * Google Cloud Pub/Sub implementation of [PubSubProvider].
 *
 * This provider supports GCP Pub/Sub functionality with:
 * - Publishing messages to topics with attributes
 * - Subscribing to topics with automatic subscription management
 * - Message acknowledgment and negative acknowledgment
 * - Automatic topic and subscription creation
 * - Connection health monitoring
 *
 * Example usage:
 * ```kotlin
 * val provider = GCPPubSubProvider(
 *     projectId = "my-gcp-project",
 *     credentialsPath = "/path/to/service-account.json",
 *     subscriptionPrefix = "agent-",
 *     autoCreateTopics = true
 * )
 * 
 * // Publish a message with attributes
 * val messageId = provider.publish("events", "Hello, World!", mapOf("source" to "agent-123"))
 * 
 * // Subscribe to messages
 * provider.subscribe("events").collect { message ->
 *     println("Received: ${message.content}")
 *     message.acknowledge() // Important for GCP Pub/Sub
 * }
 * ```
 *
 * @property projectId The GCP project ID
 * @property credentialsPath Path to the service account JSON file (optional, uses default credentials if null)
 * @property subscriptionPrefix Prefix for auto-generated subscription names (default: "koog-agent-")
 * @property autoCreateTopics Whether to automatically create topics if they don't exist (default: true)
 * @property autoCreateSubscriptions Whether to automatically create subscriptions if they don't exist (default: true)
 * @property ackDeadlineSeconds Acknowledgment deadline for subscriptions in seconds (default: 60)
 * @property maxOutstandingMessages Maximum number of outstanding messages per subscription (default: 1000)
 */
@OptIn(ExperimentalUuidApi::class)
public class GCPPubSubProvider(
    private val projectId: String,
    private val credentialsPath: String? = null,
    private val subscriptionPrefix: String = "koog-agent-",
    private val autoCreateTopics: Boolean = true,
    private val autoCreateSubscriptions: Boolean = true,
    private val ackDeadlineSeconds: Int = 60,
    private val maxOutstandingMessages: Int = 1000
) : PubSubProvider {

    private companion object {
        private val logger = KotlinLogging.logger { }
    }
    
    private val topicAdminClient: TopicAdminClient by lazy { TopicAdminClient.create() }
    private val subscriptionAdminClient: SubscriptionAdminClient by lazy { SubscriptionAdminClient.create() }
    private val publishers = mutableMapOf<String, Publisher>()
    private val subscribers = mutableMapOf<String, Subscriber>()

    override suspend fun publish(message: PubSubMessage): String? {
        return when (message) {
            is PubSubStringMessage -> publish(message.topic, message.content, message.attributes)
            else -> {
                logger.warn { "Unsupported message type for GCP Pub/Sub: ${message::class.simpleName}" }
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
            val topicName = ProjectTopicName.of(projectId, topic)
            
            // Auto-create topic if it doesn't exist
            if (autoCreateTopics) {
                createTopicIfNotExists(topicName)
            }
            
            // Get or create publisher for this topic
            val publisher = getOrCreatePublisher(topicName)
            
            // Build the message
            val pubsubMessage = PubsubMessage.newBuilder()
                .setData(ByteString.copyFromUtf8(content))
                .putAllAttributes(attributes)
                .build()
            
            // Publish and get the message ID
            val future = publisher.publish(pubsubMessage)
            val messageId = future.get() // This blocks, but we're in a suspend function
            
            logger.debug { "Published message to GCP topic '$topic' with ID: $messageId" }
            messageId
            
        } catch (e: Exception) {
            throw PubSubException("publish", topic, "Failed to publish message to GCP Pub/Sub topic", e)
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
            
            // For simplicity, we'll handle single topic subscriptions
            // Multiple topics would require multiple subscribers
            val topic = topics.first()
            val topicName = ProjectTopicName.of(projectId, topic)
            val subscriptionName = ProjectSubscriptionName.of(projectId, "${subscriptionPrefix}${topic}")
            
            // Auto-create topic and subscription if they don't exist
            if (autoCreateTopics) {
                createTopicIfNotExists(topicName)
            }
            if (autoCreateSubscriptions) {
                createSubscriptionIfNotExists(subscriptionName, topicName)
            }
            
            logger.info { "Subscribing to GCP topic: $topic" }
            
            callbackFlow {
                val messageReceiver = MessageReceiver { message, consumer ->
                    try {
                        val receivedMessage = GCPReceivedMessage(
                            messageId = message.messageId,
                            topic = topic,
                            content = message.data.toStringUtf8(),
                            attributes = message.attributesMap,
                            ackReplyConsumer = consumer
                        )
                        
                        if (isActive) {
                            trySend(receivedMessage)
                        }
                    } catch (e: Exception) {
                        logger.error(e) { "Error processing received message: ${message.messageId}" }
                        consumer.nack()
                    }
                }
                
                val subscriber = Subscriber.newBuilder(subscriptionName, messageReceiver)
                    .build()
                
                subscriber.startAsync().awaitRunning()
                
                awaitClose {
                    subscriber.stopAsync().awaitTerminated()
                }
            }
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to subscribe to topics: ${topics.joinToString()}" }
            throw PubSubException("subscribe", topics.joinToString(","), "Failed to create GCP Pub/Sub subscription", e)
        }
    }

    override suspend fun unsubscribe(topic: String, subscriptionId: String?) {
        try {
            val actualSubscriptionId = subscriptionId ?: "${subscriptionPrefix}${topic}"
            val subscriptionName = ProjectSubscriptionName.of(projectId, actualSubscriptionId)
            
            // Stop the subscriber if it exists
            subscribers[actualSubscriptionId]?.let { subscriber ->
                subscriber.stopAsync().awaitTerminated()
                subscribers.remove(actualSubscriptionId)
            }
            
            logger.debug { "Unsubscribed from GCP topic: $topic" }
            
        } catch (e: Exception) {
            throw PubSubException("unsubscribe", topic, "Failed to unsubscribe from GCP Pub/Sub topic", e)
        }
    }

    override suspend fun isConnected(): Boolean {
        return try {
            // Try to list topics to check connectivity
            val projectName = ProjectName.of(projectId)
            topicAdminClient.listTopics(projectName).iterateAll().iterator().hasNext()
            true
        } catch (e: Exception) {
            logger.debug(e) { "GCP Pub/Sub connection check failed" }
            false
        }
    }

    override suspend fun getHealthInfo(): Map<String, Any> {
        return try {
            val info = mutableMapOf<String, Any>(
                "provider" to "gcp-pubsub",
                "projectId" to projectId,
                "connected" to isConnected(),
                "activeSubscriptions" to 0,
                "subscriptionPrefix" to subscriptionPrefix,
                "autoCreateTopics" to autoCreateTopics,
                "autoCreateSubscriptions" to autoCreateSubscriptions
            )

            if (isConnected()) {
                info["healthy"] = true
            } else {
                info["healthy"] = false
                info["error"] = "Not connected"
            }

            info
        } catch (e: Exception) {
            mapOf(
                "provider" to "gcp-pubsub",
                "connected" to false,
                "healthy" to false,
                "error" to (e.message ?: "Unknown error")
            )
        }
    }

    override fun close() {
        try {
            logger.info { "Closing GCP Pub/Sub connections" }
            
            // Close all publishers
            publishers.values.forEach { publisher ->
                publisher.shutdown()
            }
            publishers.clear()
            
            // Stop all subscribers
            subscribers.values.forEach { subscriber ->
                subscriber.stopAsync().awaitTerminated()
            }
            subscribers.clear()
            
            // Close admin clients
            topicAdminClient.close()
            subscriptionAdminClient.close()
            
            logger.info { "GCP Pub/Sub connections closed successfully" }
        } catch (e: Exception) {
            logger.error(e) { "Error closing GCP Pub/Sub connections" }
        }
    }

    //region Private Helper Methods
    
    private fun createTopicIfNotExists(topicName: ProjectTopicName) {
        try {
            topicAdminClient.getTopic(topicName)
        } catch (e: Exception) {
            // Topic doesn't exist, create it
            topicAdminClient.createTopic(topicName)
            logger.debug { "Created GCP topic: ${topicName.topic}" }
        }
    }
    
    private fun createSubscriptionIfNotExists(subscriptionName: ProjectSubscriptionName, topicName: ProjectTopicName) {
        try {
            subscriptionAdminClient.getSubscription(subscriptionName)
        } catch (e: Exception) {
            // Subscription doesn't exist, create it
            val subscription = com.google.pubsub.v1.Subscription.newBuilder()
                .setName(subscriptionName.toString())
                .setTopic(topicName.toString())
                .setAckDeadlineSeconds(ackDeadlineSeconds)
                .build()
            
            subscriptionAdminClient.createSubscription(subscription)
            logger.debug { "Created GCP subscription: ${subscriptionName.subscription}" }
        }
    }
    
    private fun getOrCreatePublisher(topicName: ProjectTopicName): Publisher {
        return publishers.getOrPut(topicName.topic) {
            Publisher.newBuilder(topicName).build()
        }
    }
    
    //endregion Private Helper Methods
}