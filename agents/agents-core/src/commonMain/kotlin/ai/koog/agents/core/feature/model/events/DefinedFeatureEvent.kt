package ai.koog.agents.core.feature.model.events

import ai.koog.agents.core.feature.message.FeatureEvent
import ai.koog.agents.core.feature.message.FeatureMessage
import kotlinx.datetime.Clock

/**
 * Represents a sealed class for defining feature-related events in the system.
 *
 * This class serves as a foundational type from which specific feature events are derived.
 * Its purpose is to encapsulate shared properties and functionality across all specialized
 * feature events. Each subclass details a specific type of event, such as agent lifecycle
 * updates or processing steps.
 *
 * This class implements the [ai.koog.agents.core.feature.message.FeatureEvent] interface, ensuring compatibility with the
 * system's feature event handling mechanisms.
 *
 * @constructor Initializes a new instance of the `DefinedFeatureEvent` class.
 */
public sealed class DefinedFeatureEvent : FeatureEvent {
    /**
     * The timestamp, represented as the number of milliseconds since the Unix epoch,
     * indicating when the feature event was created.
     *
     * This property is used to track the exact creation or occurrence time of the feature event.
     * It provides temporal context for ordering events, correlating feature messages, and
     * performing time-based analysis within the system.
     */
    override val timestamp: Long = Clock.System.now().toEpochMilliseconds()

    /**
     * Specifies the type of the feature message for this event.
     *
     * This property is overridden to indicate that the message type of this
     * feature is categorized as an `Event`. The `Event` type is used to represent
     * occurrences or actions within the system, providing context to event-specific
     * feature messages.
     */
    override val messageType: FeatureMessage.Type = FeatureMessage.Type.Event
}
