package ai.koog.a2a.model

import kotlinx.serialization.Serializable

/**
 * Base interface for events.
 */
@Serializable(with = EventSerializer::class)
public sealed interface Event {
    /**
     * The type used as discriminator.
     */
    public val kind: String
}

/**
 * Base interface for communication events, such as messages or tasks.
 */
@Serializable(with = CommunicationEventSerializer::class)
public sealed interface CommunicationEvent : Event

/**
 * Base interface for task events.
 */
@Serializable(with = TaskEventSerializer::class)
public sealed interface TaskEvent : Event {
    /**
     * The ID of the task associated with this event.
     */
    public val taskId: String

    /**
     * The ID of the context associated with this event.
     */
    public val contextId: String
}
