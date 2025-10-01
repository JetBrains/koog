package ai.koog.a2a.server.session

import ai.koog.a2a.model.Event
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskEvent
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.server.exceptions.InvalidEventException
import ai.koog.a2a.server.exceptions.SessionNotActiveException
import ai.koog.a2a.server.tasks.TaskStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.jvm.JvmInline

/**
 * A session processor responsible for handling session events.
 * It validates the events, writes them to [taskStorage] and emits them to the subscribers via [events].
 *
 * Validation logic attempts to perform basic verification that events follow what is expected from a proper A2A server implementation.
 * These are the main rules:
 *
 * - **Session type exclusivity**: A session can only handle either [Message] event or [TaskEvent] events, never both.
 * - **Context ID validation**: All events must have the same context id as provided [contextId].
 * - **Single message limit**: Only one [Message] can be sent per session, after which the processor closes.
 * - **Task ID consistency**: [TaskEvent] events must have task ids equal to provided [taskId].
 * - **Final event enforcement**: After a [TaskStatusUpdateEvent] with `final=true` is sent, processor closes.
 * - **Terminal state closure**: When the event with the terminal state is sent, processor closes.
 *
 * @param taskStorage The storage where task events will be saved.
 * @property contextId The contextId associated with this session, representing either an existing context
 * from the incoming request or a newly generated ID that must be used for all events in this session.
 * @property taskId The taskId associated with this session, representing either an existing task
 * from the incoming request or a newly generated ID that must be used if creating a new task.
 * Note: This taskId might not correspond to an actually existing task initially - it serves as the
 * identifier that will be validated against all [TaskEvent] in this session.
 */
@OptIn(ExperimentalAtomicApi::class)
public class SessionEventProcessor(
    public val contextId: String,
    public val taskId: String,
    private val taskStorage: TaskStorage,
) {
    private companion object {
        private const val SESSION_CLOSED = "Session event processor is closed, can't send events"

        private const val INVALID_CONTEXT_ID = "Event contextId must be same as provided contextId"

        private const val INVALID_TASK_ID = "Event taskId must be same as provided taskId"

        private const val TASK_EVENT_SENT =
            "Task has already been initialized in this session, only TaskEvent's with the same taskId can be sent from now on"
    }

    private val _isOpen: AtomicBoolean = AtomicBoolean(true)

    /**
     * Whether the session is open.
     */
    public val isOpen: Boolean get() = _isOpen.load()

    /**
     * Tracks whether a task event was sent in this session, meaning we have to reject [Message] events now.
     */
    private var isTaskEventSent: Boolean = false

    private val sessionMutex = Mutex()

    /**
     * Helper interface to send actual events or termination signal to close current event stream subscribers on session closure.
     */
    private sealed interface FlowEvent {
        @JvmInline
        value class Data(val data: Event) : FlowEvent
        object Close : FlowEvent
    }

    private val _events = MutableSharedFlow<FlowEvent>()

    /**
     * A hot flow of events in this session that can be subscribed to.
     */
    public val events: Flow<Event> = _events
        .onSubscription { if (!_isOpen.load()) emit(FlowEvent.Close) }
        .takeWhile { it !is FlowEvent.Close }
        .filterIsInstance<FlowEvent.Data>()
        .map { it.data }

    /**
     * Sends a [Message] to the session event processor. Validates the message against the session context and updates
     * the session state accordingly.
     *
     * @param message The message to be sent.
     * @throws [InvalidEventException] for invalid events.
     * @see SessionEventProcessor
     */
    public suspend fun sendMessage(message: Message): Unit = sessionMutex.withLock {
        if (_isOpen.load()) {
            if (isTaskEventSent) {
                throw InvalidEventException(TASK_EVENT_SENT)
            }

            if (message.contextId != this.contextId) {
                throw InvalidEventException(INVALID_CONTEXT_ID)
            }

            _events.emit(FlowEvent.Data(message))
            _isOpen.store(false)
        } else {
            throw SessionNotActiveException(SESSION_CLOSED)
        }
    }

    /**
     * Sends a [TaskEvent] to the session event processor.
     * Validates the event against the session context and updates [taskStorage].
     *
     * @param event The event to be sent.
     * @throws [InvalidEventException] for invalid events.
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

            taskStorage.update(event)
            _events.emit(FlowEvent.Data(event))

            val isFinalEvent = (event is TaskStatusUpdateEvent && (event.status.state.terminal || event.final)) ||
                (event is Task && event.status.state.terminal)

            if (isFinalEvent) {
                _isOpen.store(false)
            }
        } else {
            throw SessionNotActiveException(SESSION_CLOSED)
        }
    }

    /**
     * Closes the session event processor, also closing event stream.
     */
    public suspend fun close(): Unit = sessionMutex.withLock {
        _isOpen.store(false)
        _events.emit(FlowEvent.Close)
    }
}
