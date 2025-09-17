package ai.koog.agents.core.feature.model

import ai.koog.agents.core.feature.message.FeatureEvent
import ai.koog.agents.core.feature.message.FeatureMessage
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

/**
 * A data class representing a feature event message that encapsulates details about a specific event.
 *
 * This class implements the [ai.koog.agents.core.feature.message.FeatureEvent] interface, which extends the [ai.koog.agents.core.feature.message.FeatureMessage] interface,
 * indicating that it contains event-specific information and adheres to the structure of a feature message.
 *
 * The primary purpose of this class is to represent feature event data with an associated unique event identifier,
 * a timestamp marking its creation, and the message type indicating it is an event-specific message.
 *
 * @property eventId A unique identifier associated with this event.
 *                   This property implements the [ai.koog.agents.core.feature.message.FeatureEvent.eventId] from the parent interface.
 * @property timestamp The time at which this event message was created has represented in milliseconds since the epoch.
 *                     This property implements the [ai.koog.agents.core.feature.message.FeatureMessage.timestamp] from the parent interface.
 * @property messageType The type of the message, which in this case is fixed as [Type.Event].
 *                       This property implements the [ai.koog.agents.core.feature.message.FeatureMessage.messageType] from the parent interface.
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
     * in [Type]. In this case, it signifies that the message is classified as an event.
     *
     * Primarily used to determine the behavior or handling applicable to the specific type of message.
     */
    override val messageType: FeatureMessage.Type = FeatureMessage.Type.Event
}
