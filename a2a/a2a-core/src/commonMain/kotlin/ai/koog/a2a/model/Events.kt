package ai.koog.a2a.model

/**
 * Base interface for all A2A events.
 */
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
public sealed interface ResponseEvent : Event

/**
 * Base interface for task events.
 */
public sealed interface TaskEvent : Event {
    override val taskId: String
    override val contextId: String
}
