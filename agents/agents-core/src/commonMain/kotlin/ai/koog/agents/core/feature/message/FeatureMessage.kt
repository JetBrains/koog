package ai.koog.agents.core.feature.message

/**
 * Represents a feature message or event within the system.
 *
 * This interface serves as a base contract for feature messages that encapsulate
 * information about various system events or updates, such as status changes or interaction events.
 */
public interface FeatureMessage {

    /**n
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
