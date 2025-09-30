package ai.koog.agents.core.feature.message

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
