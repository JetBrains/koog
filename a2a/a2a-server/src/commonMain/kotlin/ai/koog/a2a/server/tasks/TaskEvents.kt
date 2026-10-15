package ai.koog.a2a.server.tasks

import ai.koog.a2a.model.Event
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskStatusUpdateEvent

/**
 * Whether this event puts its task into a terminal state, i.e. it is a [Task] or a [TaskStatusUpdateEvent] whose
 * status state is [terminal][ai.koog.a2a.model.TaskState.terminal].
 */
internal fun Event.isTerminal(): Boolean = when (this) {
    is Task -> status.state.terminal
    is TaskStatusUpdateEvent -> status.state.terminal
    else -> false
}
