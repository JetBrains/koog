package ai.koog.agents.core.feature.message

import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

/**
 * Represents a feature message or event within the system.
 *
 * This interface serves as a base contract for feature messages that encapsulate
 * information about various system events or updates, such as status changes or interaction events.
 */
public interface FeatureMessage {

    /**
     * Represents the time, in milliseconds, when the feature message or event was created or occurred.
     *
     * The timestamp is used to track the exact time of the message's creation or event's occurrence,
     * facilitating temporal analysis, ordering, or correlation within the system.
     */
    public val timestamp: Long

    /**
     * Specifies the type of feature message or event.
     *
     * This property is used to categorize messages into predefined types within the system,
     * such as `Message` or `Event`. The type determines how the message should be processed
     * or interpreted by the underlying handlers or processors.
     */
    public val messageType: Type

    /**
     * Represents the type of feature message or event.
     *
     * This enum class is used to categorize and distinguish the kinds of messages
     * processed within the system. It contains predefined values for message and event types.
     *
     * @property value The string representation of the type.
     */
    public enum class Type(public val value: String) {

        /**
         * Represents a message with a text content.
         *
         * The `Message` class encapsulates a string that represents
         * a textual message. This can be used for various purposes such
         * as displaying messages, sending messages over a network, logging,
         * or any other scenario where text-based content needs to be handled.
         */
        Message("message"),

        /**
         * Represents an event with a specific name or identifier.
         *
         * This class serves as a structure to encapsulate information about a specific
         * occurrence or action. The name of the event can be used to differentiate
         * between various events in a system and can be used for event handling or
         * triggering specific processes.
         */
        Event("event")
    }
}

/**
 * Represents a specialized type of feature message that corresponds to an event in the system.
 * A feature event typically carries information uniquely identifying the event, alongside other
 * data provided by the [FeatureMessage] interface.
 *
 * Implementations of this interface are intended to detail specific events in the feature
 * processing workflow.
 */
public interface FeatureEvent : FeatureMessage {

    /**
     * Represents a unique identifier for a feature-related event.
     *
     * This identifier is used to distinguish and track individual events in the system,
     * enabling a clear correlation between logged events or processed messages.
     */
    public val eventId: String
}

/**
 * Represents a detailed implementation of [FeatureMessage] that encapsulates a string message.
 *
 * This class associates a string content with a specific feature-related message type, along with
 * a timestamp indicating when the message was created.
 *
 * It is primarily used for text-based feature messages and integrates with the [FeatureMessage]
 * interface to define its structure.
 *
 * Instances of this type are timestamped at the moment of their creation, ensuring consistent
 * temporal tracking for feature messages.
 *
 * @property message The textual message content encapsulated by this feature message.
 */
@Serializable
public data class FeatureStringMessage(val message: String) : FeatureMessage {

    /**
     * The timestamp, represented in milliseconds since the epoch, indicating when the
     * feature message was created.
     *
     * This property provides a temporal reference that can be used to track or order
     * the occurrence of feature messages or events. The timestamp is generated using
     * the system clock at the time of initialization.
     */
    override val timestamp: Long = Clock.System.now().toEpochMilliseconds()

    /**
     * Represents the type of the feature message, identifying the message's purpose or category.
     *
     * In this implementation, the `messageType` property is set to `FeatureMessage.Type.Message`,
     * which classifies the message as a standard feature-related message.
     *
     * The `messageType` property enables proper categorization and handling of feature messages
     * within the system, facilitating streamlined processing and functionality differentiation
     * based on the message type.
     */
    override val messageType: FeatureMessage.Type = FeatureMessage.Type.Message
}

/**
 * A data class representing a feature event message that encapsulates details about a specific event.
 *
 * This class implements the [FeatureEvent] interface, which extends the [FeatureMessage] interface,
 * indicating that it contains event-specific information and adheres to the structure of a feature message.
 *
 * The primary purpose of this class is to represent feature event data with an associated unique event identifier,
 * a timestamp marking its creation, and the message type indicating it is an event-specific message.
 *
 * @property eventId A unique identifier associated with this event.
 *                   This property implements the [FeatureEvent.eventId] from the parent interface.
 * @property timestamp The time at which this event message was created has represented in milliseconds since the epoch.
 *                     This property implements the [FeatureMessage.timestamp] from the parent interface.
 * @property messageType The type of the message, which in this case is fixed as [FeatureMessage.Type.Event].
 *                       This property implements the [FeatureMessage.messageType] from the parent interface.
 */
@Serializable
public data class FeatureEventMessage(override val eventId: String) : FeatureEvent {

    /**
     * Represents the timestamp of when the feature event message was created.
     *
     * The timestamp is defined as the number of milliseconds elapsed since the Unix epoch
     * (1970-01-01T00:00:00Z), as provided by the system clock.
     * This value is used to record the exact time of occurrence for feature events, enabling
     * precise tracking and ordering of event processing.
     */
    override val timestamp: Long = Clock.System.now().toEpochMilliseconds()

    /**
     * Specifies the type of the feature message, indicating the nature of the message being processed.
     *
     * This property uniquely identifies the message classification based on the predefined types
     * in [FeatureMessage.Type]. In this case, it signifies that the message is classified as an event.
     *
     * Primarily used to determine the behavior or handling applicable to the specific type of message.
     */
    override val messageType: FeatureMessage.Type = FeatureMessage.Type.Event
}
