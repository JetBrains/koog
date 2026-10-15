package ai.koog.a2a.server.tasks

import ai.koog.a2a.model.Event
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskStatusUpdateEvent

/** Whether this event is a [Task] or [TaskStatusUpdateEvent] with a [terminal][ai.koog.a2a.model.TaskState.terminal] state. */
internal fun Event.isTerminal(): Boolean = when (this) {
    is Task -> status.state.terminal
    is TaskStatusUpdateEvent -> status.state.terminal
    else -> false
}
