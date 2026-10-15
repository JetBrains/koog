package ai.koog.a2a.server.session

import ai.koog.a2a.model.Message
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskEvent
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.server.exceptions.InvalidEventException
import ai.koog.a2a.server.exceptions.SessionNotActiveException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * A session processor responsible for handling the events the agent sends.
 * It validates the events and publishes them to the task's event stream, which persists them to the task storage and
 * delivers them to every stream of the task: the current turn's [Session.events] and all SubscribeToTask streams.
 *
 * Validation logic attempts to perform basic verification that events follow what is expected from a proper A2A server implementation.
 * These are the main rules:
 *
 * - **Session type exclusivity**: A session can only handle either [Message] event or [TaskEvent] events, never both.
 * - **Existing task**: When the turn continues an existing task, the session handles [TaskEvent] events only: the
 *   agent may send status and artifact updates of the task right away, without sending a [Task] first, and can't
 *   reply with a [Message].
 * - **Context ID validation**: All events must have the same context id as provided [contextId].
 * - **Single message limit**: Only one [Message] can be sent per session, after which the processor closes.
 * - **Task ID consistency**: [TaskEvent] events must have task ids equal to provided [taskId].
 * - **Terminal state closure**: When the event with the terminal state is sent, processor closes.
 * - **Turn closure**: When the turn ends, i.e. [ai.koog.a2a.server.agent.AgentExecutor.execute] returns, the processor
 *   of the turn closes.
 *
 * Instances are created by [SessionManager].
 *
 * @property contextId The contextId associated with this session, representing either an existing context
 * from the incoming request or a newly generated ID that must be used for all events in this session.
 * @property taskId The taskId associated with this session, representing either an existing task
 * from the incoming request or a newly generated ID that must be used if creating a new task.
 * Note: This taskId might not correspond to an actually existing task initially - it serves as the
 * identifier that will be validated against all [TaskEvent] in this session.
 * @param taskExists Whether the task [taskId] already exists when the processor is created, i.e. the processor starts
 * in task mode and rejects [Message] events.
 */
@OptIn(ExperimentalAtomicApi::class)
public class SessionEventProcessor internal constructor(
    public val contextId: String,
    public val taskId: String,
    private val stream: TaskEventStream,
    private val turnId: Long?,
    private val taskExists: Boolean = false,
) {
    private companion object {
        private const val SESSION_CLOSED = "Session event processor is closed, can't send events"

        private const val INVALID_CONTEXT_ID = "Event contextId must be same as provided contextId"

        private const val INVALID_TASK_ID = "Event taskId must be same as provided taskId"

        private const val TASK_EVENT_SENT =
            "Task has already been initialized in this session, only TaskEvent's with the same taskId can be sent from now on"

        private const val TASK_EXISTS =
            "Task already exists, only TaskEvent's with the same taskId can be sent in this session"
    }

    init {
        require(stream.taskId == taskId) { "Event stream of task '${stream.taskId}' can't be used for task '$taskId'" }
    }

    private val _isOpen: AtomicBoolean = AtomicBoolean(true)

    /**
     * Whether the processor still accepts events.
     */
    internal val isOpen: Boolean get() = _isOpen.load()

    /**
     * Tracks whether a task event was sent through this processor, meaning we have to reject [Message] events now.
     */
    internal var isTaskEventSent: Boolean = false
        private set

    private val sessionMutex = Mutex()

    /**
     * Sends a [Message] to the session event processor. Validates the message against the session context and updates
     * the session state accordingly. The message is delivered only to the current turn's [Session.events]; it is not
     * part of the task and is not persisted.
     *
     * @param message The message to be sent.
     * @throws [InvalidEventException] for invalid events.
     * @throws [SessionNotActiveException] if the processor is closed.
     * @see SessionEventProcessor
     */
    public suspend fun sendMessage(message: Message): Unit = sessionMutex.withLock {
        if (_isOpen.load()) {
            if (taskExists) {
                throw InvalidEventException(TASK_EXISTS)
            }

            if (isTaskEventSent) {
                throw InvalidEventException(TASK_EVENT_SENT)
            }

            if (message.contextId != this.contextId) {
                throw InvalidEventException(INVALID_CONTEXT_ID)
            }

            stream.publish(message, turnId)
            _isOpen.store(false)
        } else {
            throw SessionNotActiveException(SESSION_CLOSED)
        }
    }

    /**
     * Sends a [TaskEvent] to the session event processor.
     * Validates the event against the session context, persists it to the task storage, and delivers it to all streams
     * of the task. When this method returns, the event is persisted.
     *
     * @param event The event to be sent.
     * @throws [InvalidEventException] for invalid events.
     * @throws [SessionNotActiveException] if the processor is closed.
     * @see SessionEventProcessor
     */
    public suspend fun sendTaskEvent(event: TaskEvent): Unit = sessionMutex.withLock {
        if (_isOpen.load()) {
            isTaskEventSent = true

            if (event.contextId != this.contextId) {
                throw InvalidEventException(INVALID_CONTEXT_ID)
            }

            if (event.taskId != this.taskId) {
                throw InvalidEventException(INVALID_TASK_ID)
            }

            stream.publish(event, turnId)

            val isFinalEvent = (event is TaskStatusUpdateEvent && event.status.state.terminal) ||
                (event is Task && event.status.state.terminal)

            if (isFinalEvent) {
                _isOpen.store(false)
            }
        } else {
            throw SessionNotActiveException(SESSION_CLOSED)
        }
    }

    /**
     * Closes the processor: subsequent events are rejected with [SessionNotActiveException].
     */
    internal suspend fun close(): Unit = sessionMutex.withLock {
        _isOpen.store(false)
    }
}
