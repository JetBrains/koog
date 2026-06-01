package ai.koog.a2a.model

import ai.koog.a2a.serialization.EventSerializer
import ai.koog.a2a.serialization.ResponseEventSerializer
import ai.koog.a2a.serialization.TaskEventSerializer
import kotlinx.serialization.Serializable

/**
 * Base interface for all A2A events.
 */
@Serializable(with = EventSerializer::class)
public sealed interface Event {
    /**
     * The ID of the task associated with this event.
     */
    public val taskId: String?

    /**
     * The ID of the context associated with this event.
     */
    public val contextId: String?
}

/**
 * Base interface for events that can also be returned by non-streaming send message operations.
 */
@Serializable(with = ResponseEventSerializer::class)
public sealed interface ResponseEvent : Event

/**
 * Base interface for task events.
 */
@Serializable(with = TaskEventSerializer::class)
public sealed interface TaskEvent : Event {
    override val taskId: String
    override val contextId: String
}
