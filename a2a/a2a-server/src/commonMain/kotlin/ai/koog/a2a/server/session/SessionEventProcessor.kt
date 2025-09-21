package ai.koog.a2a.server.session

import ai.koog.a2a.model.Event
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskArtifactUpdateEvent
import ai.koog.a2a.model.TaskEvent
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.server.exceptions.InvalidEventException
import ai.koog.a2a.server.exceptions.SessionClosedException
import ai.koog.a2a.server.tasks.TaskStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.jvm.JvmInline

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
 * - **Task ID consistency**: [TaskEvent] events must have task ids equal to [taskId] provided for this session.
 * - **Final event enforcement**: After a [TaskStatusUpdateEvent] with `final=true` is sent, no more events are permitted
 * - **Terminal state blocking**: No events can be sent when the task is already in a terminal state
 * - **Final flag requirement**: [TaskStatusUpdateEvent]s that set the task to a terminal state must have `final=true`
 *
 * @property contextId The contextId associated with this session, representing either an existing context
 * from the incoming request or a newly generated ID that must be used for all events in this session.
 * @property taskId The taskId associated with this session, representing either an existing task
 * from the incoming request or a newly generated ID that must be used if creating a new task.
 * Note: This taskId might not correspond to an actually existing task initially - it serves as the
 * identifier that will be validated against all [TaskEvent] in this session.
 * @param taskStorage The storage for tasks where task events will be saved.
 * @param task The initial task associated with the session, if it is a continuation of a previous task session.
 *
 * @property events A hot flow of events in this session that can be subscribed to.
 */
public class SessionEventProcessor(
    public val contextId: String,
    public val taskId: String,
    private val taskStorage: TaskStorage,
    private val task: Task? = null,
) {
    private companion object {
        private const val SESSION_CLOSED = "Session event processor is closed, can't send events"

        private const val INVALID_CONTEXT_ID = "Event contextId must be same as provided contextId"

        private const val INVALID_TASK_ID = "Event taskId must be same as provided taskId"

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
    }

    /**
     * Helper interface to handle different session types.
     */
    private sealed interface SessionType {
        object MessageSession : SessionType

        class TaskSession(
            val taskId: String,
            var taskState: TaskState? = null,
            var finalEventReceived: Boolean = false,
        ) : SessionType
    }

    /**
     * Helper interface to send actual events or termination signal to cancel events stream on session closure.
     */
    private sealed interface FlowEvent {
        @JvmInline
        value class Data(val data: Event) : FlowEvent
        object Cancel : FlowEvent
    }

    private val isClosed = MutableStateFlow(false)

    private val _events = MutableSharedFlow<FlowEvent>()
    public val events: Flow<Event>
        get() = flow {
            if (!isClosed.value) {
                emitAll(
                    _events
                        .takeWhile { !isClosed.value }
                        .filterIsInstance<FlowEvent.Data>()
                        .map { it.data }
                )
            }
        }

    private val sessionMutex = Mutex()
    private var sessionType: SessionType? = task?.let {
        SessionType.TaskSession(
            taskId = it.id,
            taskState = it.status.state
        )
    }

    /**
     * Sends a [Message] to the session event processor. Validates the message against the session context and updates
     * the session state accordingly.
     *
     * @param message The message to be sent.
     * @throws [InvalidEventException] for invalid events.
     * Check [SessionEventProcessor] docs from info about valid events.
     */
    public suspend fun sendMessage(message: Message): Unit = sessionMutex.withLock {
        if (isClosed.value) {
            throw SessionClosedException(SESSION_CLOSED)
        }

        if (message.contextId != contextId) {
            throw InvalidEventException(INVALID_CONTEXT_ID)
        }

        when (sessionType) {
            is SessionType.MessageSession -> throw InvalidEventException(MESSAGE_SENT)

            is SessionType.TaskSession -> throw InvalidEventException(TASK_INITIALIZED)

            null -> {
                _events.emit(FlowEvent.Data(message))
                sessionType = SessionType.MessageSession
            }
        }
    }

    /**
     * Sends a [TaskEvent] to the session event processor. Validates the event against the session context and updates
     * the session state and [taskStorage] accordingly.
     *
     * @param event The event to be sent.
     * @throws [InvalidEventException] for invalid events.
     * Check [SessionEventProcessor] docs from info about valid events.
     */
    public suspend fun sendTaskEvent(event: TaskEvent): Unit = sessionMutex.withLock {
        if (isClosed.value) {
            throw SessionClosedException(SESSION_CLOSED)
        }

        if (event.contextId != contextId) {
            throw InvalidEventException(INVALID_CONTEXT_ID)
        }

        if (event.taskId != taskId) {
            throw InvalidEventException(INVALID_TASK_ID)
        }

        /*
          The first set of checks, to get initial task session type if it is allowed here.
         */
        val taskSessionType: SessionType.TaskSession = when (sessionType) {
            is SessionType.MessageSession -> throw InvalidEventException(MESSAGE_SENT)

            is SessionType.TaskSession -> sessionType as SessionType.TaskSession

            null -> {
                SessionType.TaskSession(
                    taskId = event.taskId,
                    taskState = task?.status?.state, // null - new task
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
        _events.emit(FlowEvent.Data(event))

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

    public suspend fun close(): Unit = sessionMutex.withLock {
        isClosed.value = true
        _events.emit(FlowEvent.Cancel)
    }
}
