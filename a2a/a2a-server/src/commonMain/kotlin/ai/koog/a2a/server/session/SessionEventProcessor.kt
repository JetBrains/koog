package ai.koog.a2a.server.session

import ai.koog.a2a.model.Event
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskArtifactUpdateEvent
import ai.koog.a2a.model.TaskEvent
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.server.exceptions.InvalidEventException
import ai.koog.a2a.server.tasks.TaskStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A session processor responsible for handling session events.
 * It validates the events, emits them to the subscribers via [events] and updates session state.
 * All valid [TaskEvent] events that are sent using [sendTaskEvent] will also be saved to the [taskStorage] provided.
 *
 * Validation logic attempts to verify that the number, type and order of events comply to what is expected from a proper
 * A2A server implementation.
 * These are the main rules:
 *
 * - **Session type exclusivity**: A session can only handle either [Message] events or [TaskEvent] events, never both
 * - **Context ID validation**: All events must have the same contextId as the session
 * - **Single message limit**: Only one [Message] can be sent per session, after which the session becomes terminal
 * - **Task initialization order**: For new tasks, the first [TaskEvent] must be of type [Task] to create the task
 * - **Task ID consistency**: Once a task session is initialized, only [TaskEvent]s with the same taskId are allowed
 * - **Final event enforcement**: After a [TaskStatusUpdateEvent] with `final=true` is sent, no more events are permitted
 * - **Terminal state blocking**: No events can be sent when the task is already in a terminal state
 * - **Final flag requirement**: [TaskStatusUpdateEvent]s that set the task to a terminal state must have `final=true`
 *
 * @property contextId The contextId of the session.
 * @param taskStorage The storage for tasks where task events will be saved.
 * @param coroutineScope The scope in which the event flow will be shared
 * @param currentTask The current task associated with the session, if it is a continuation of a previous task session.
 *
 * @property events A shared flow of session events that can be subscribed. The flow will be closed when the session is closed.
 */
public class SessionEventProcessor(
    public val contextId: String,
    private val taskStorage: TaskStorage,
    coroutineScope: CoroutineScope,
    currentTask: Task? = null,
) : AutoCloseable {
    private companion object {
        private const val MESSAGE_SENT =
            "Message has already been sent in this session. Sending message is a terminal operation and no more events " +
                "are allowed to be sent, the session must terminate ASAP"

        private const val TASK_INITIALIZED =
            "Task has already been initialized in this sessions, only TaskEvent's with the same taskId can be sent from now on."

        private const val TASK_EVENT_FINAL_SENT =
            "Final TaskEvent has already been sent in this session. Sending final event is a terminal operation " +
                "and no more events are allowed to be sent, the session must terminate ASAP"

        private const val TASK_EVENT_TERMINAL_STATE =
            "TaskEvent's cannot be sent when the task transitioned to the terminal state."

        private const val TASK_EVENT_FINAL_REQUIRED =
            "TaskEvent final parameter is required to be set to 'true' when setting task state to the terminal state"

        private const val TASK_DOES_NOT_EXIST =
            "Task associated with the taskId in TaskEvent does not exist yet and the event was not Task. Creating new " +
                "task should always start with Task event."

        private const val INVALID_CONTEXT_ID = "Event contextId must be same as current contextId"
    }

    private sealed interface SessionType {
        object MessageSession : SessionType

        class TaskSession(
            val taskId: String,
            var taskState: TaskState? = null,
            var finalEventReceived: Boolean = false,
        ) : SessionType
    }

    private val _events = Channel<Event>()
    public val events: SharedFlow<Event> = _events
        .receiveAsFlow()
        .shareIn(scope = coroutineScope, started = SharingStarted.Eagerly)

    private val sessionMutex = Mutex()
    private var sessionType: SessionType? = currentTask?.let {
        SessionType.TaskSession(
            taskId = it.id,
            taskState = it.status.state
        )
    }

    /**
     * Sends a [Message] to the session event processor. Validates the message against the session context and updates
     * the session state accordingly.
     *
     * @param message The message to be sent. Contains details such as message content, context ID, and metadata.
     * @throws [InvalidEventException] for invalid events.
     * Check [SessionEventProcessor] docs from info about valid events.
     */
    public suspend fun sendMessage(message: Message): Unit = sessionMutex.withLock {
        if (message.contextId != contextId) {
            throw InvalidEventException(INVALID_CONTEXT_ID)
        }

        when (sessionType) {
            is SessionType.MessageSession -> throw InvalidEventException(MESSAGE_SENT)

            is SessionType.TaskSession -> throw InvalidEventException(TASK_INITIALIZED)

            null -> {
                _events.send(message)
                sessionType = SessionType.MessageSession
            }
        }
    }

    /**
     * Sends a [TaskEvent] to the session event processor. Validates the event against the session context and updates
     * the session state and [taskStorage] accordingly.
     *
     * @param event The event to be sent. Contains details such as task ID, context ID, and metadata.
     * @throws [InvalidEventException] for invalid events.
     * Check [SessionEventProcessor] docs from info about valid events.
     */
    public suspend fun sendTaskEvent(event: TaskEvent): Unit = sessionMutex.withLock {
        if (event.contextId != contextId) {
            throw InvalidEventException(INVALID_CONTEXT_ID)
        }
        /*
          The first set of checks, to get initial task session type if it is allowed here.
         */
        val taskSessionType: SessionType.TaskSession = when (sessionType) {
            is SessionType.MessageSession -> throw InvalidEventException(MESSAGE_SENT)

            is SessionType.TaskSession -> sessionType as SessionType.TaskSession

            null -> {
                val savedTask = taskStorage.get(event.taskId, historyLength = 0, includeArtifacts = false)

                SessionType.TaskSession(
                    taskId = event.taskId,
                    taskState = savedTask?.status?.state, // null - new task
                    finalEventReceived = false
                ).also {
                    sessionType = it
                }
            }
        }

        /*
          The second set of checks to check various aspects of the current task and session state and guide the user to emit
          only allowed events.
         */
        when {
            /**
             * If the task does not exist yet, the first [TaskEvent] should be only of type Task, to create the task itself
             */
            taskSessionType.taskState == null && event !is Task ->
                throw InvalidEventException(TASK_DOES_NOT_EXIST)

            /**
             * If there was already a [TaskStatusUpdateEvent] with [TaskStatusUpdateEvent.final] set to true, no more events are expected
             */
            taskSessionType.finalEventReceived ->
                throw InvalidEventException(TASK_EVENT_FINAL_SENT)

            /**
             * If the task is already in a terminal state, no more events are expected
             */
            taskSessionType.taskState?.terminal == true ->
                throw InvalidEventException(TASK_EVENT_TERMINAL_STATE)

            /**
             * If the event is a [TaskStatusUpdateEvent] attempting to set a task to a terminal state,
             * then [TaskStatusUpdateEvent.final] must be set to true
             */
            event is TaskStatusUpdateEvent && event.status.state.terminal && !event.final ->
                throw InvalidEventException(TASK_EVENT_FINAL_REQUIRED)
        }

        // Only if all checks passed, attempt to update and emit the event
        taskStorage.update(event)
        _events.send(event)

        when (event) {
            is TaskStatusUpdateEvent -> taskSessionType.apply {
                taskState = event.status.state
                finalEventReceived = event.final
            }

            is Task -> taskSessionType.apply {
                taskState = event.status.state
            }

            is TaskArtifactUpdateEvent -> {
                // do nothing, condition is left here for clarity
            }
        }
    }

    override fun close() {
        _events.close()
    }
}
