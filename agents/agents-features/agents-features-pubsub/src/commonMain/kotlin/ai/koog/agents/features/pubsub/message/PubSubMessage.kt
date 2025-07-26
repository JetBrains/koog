package ai.koog.agents.features.pubsub.message

import ai.koog.agents.features.common.message.FeatureEvent
import ai.koog.agents.features.common.message.FeatureMessage
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

/**
 * Represents a message that can be published to or received from a PubSub system.
 *
 * This interface extends [FeatureMessage] to provide PubSub-specific functionality while
 * maintaining compatibility with the broader feature message system.
 */
public interface PubSubMessage : FeatureMessage {
    /**
     * The topic/channel this message belongs to.
     */
    public val topic: String
    
    /**
     * Optional attributes/headers associated with this message.
     */
    public val attributes: Map<String, String>
        get() = emptyMap()
}

/**
 * Represents an event related to PubSub operations.
 */
public interface PubSubEvent : FeatureEvent, PubSubMessage

/**
 * A concrete implementation of [PubSubMessage] for string-based content.
 *
 * @property topic The topic/channel this message belongs to
 * @property content The message content
 * @property attributes Optional message attributes/headers
 */
@Serializable
public data class PubSubStringMessage(
    override val topic: String,
    val content: String,
    override val attributes: Map<String, String> = emptyMap()
) : PubSubMessage {
    override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    override val messageType: FeatureMessage.Type = FeatureMessage.Type.Message
}

/**
 * Event fired when a message is published to a PubSub system.
 *
 * @property eventId Unique identifier for this publish event
 * @property topic The topic the message was published to
 * @property messageId The ID assigned by the PubSub provider (if available)
 * @property attributes Message attributes/headers
 */
@Serializable
public data class MessagePublishedEvent(
    override val eventId: String,
    override val topic: String,
    val messageId: String? = null,
    override val attributes: Map<String, String> = emptyMap()
) : PubSubEvent {
    override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    override val messageType: FeatureMessage.Type = FeatureMessage.Type.Event
}

/**
 * Event fired when a message is received from a PubSub system.
 *
 * @property eventId Unique identifier for this receive event
 * @property topic The topic the message was received from
 * @property messageId The ID assigned by the PubSub provider
 * @property content The message content
 * @property attributes Message attributes/headers
 */
@Serializable
public data class MessageReceivedEvent(
    override val eventId: String,
    override val topic: String,
    val messageId: String,
    val content: String,
    override val attributes: Map<String, String> = emptyMap()
) : PubSubEvent {
    override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    override val messageType: FeatureMessage.Type = FeatureMessage.Type.Event
}

/**
 * Event fired when a subscription is established to a topic.
 *
 * @property eventId Unique identifier for this subscription event
 * @property topic The topic that was subscribed to
 * @property subscriptionId The subscription ID (provider-specific)
 */
@Serializable
public data class SubscriptionCreatedEvent(
    override val eventId: String,
    override val topic: String, 
    val subscriptionId: String,
    override val attributes: Map<String, String> = emptyMap()
) : PubSubEvent {
    override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    override val messageType: FeatureMessage.Type = FeatureMessage.Type.Event
}

/**
 * Event fired when an error occurs during PubSub operations.
 *
 * @property eventId Unique identifier for this error event
 * @property topic The topic related to the error (if applicable)
 * @property operation The operation that failed (publish, subscribe, etc.)
 * @property error Error details
 */
@Serializable
public data class PubSubErrorEvent(
    override val eventId: String,
    override val topic: String,
    val operation: String,
    val error: String,
    override val attributes: Map<String, String> = emptyMap()
) : PubSubEvent {
    override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    override val messageType: FeatureMessage.Type = FeatureMessage.Type.Event
}