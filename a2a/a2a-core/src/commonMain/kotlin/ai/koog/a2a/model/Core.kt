package ai.koog.a2a.model

import ai.koog.a2a.serialization.CommunicationEventSerializer
import ai.koog.a2a.serialization.EventSerializer
import ai.koog.a2a.serialization.TaskEventSerializer
import kotlinx.serialization.Serializable

/**
 * Base interface for events.
 */
@Serializable(with = EventSerializer::class)
public sealed interface Event

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
