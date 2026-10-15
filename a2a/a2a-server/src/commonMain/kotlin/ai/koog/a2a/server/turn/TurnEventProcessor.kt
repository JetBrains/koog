package ai.koog.a2a.server.turn

import ai.koog.a2a.model.Message
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskEvent
import ai.koog.a2a.server.exceptions.InvalidEventException
import ai.koog.a2a.server.exceptions.TaskOperationException
import ai.koog.a2a.server.exceptions.TurnNotActiveException
import ai.koog.a2a.server.tasks.isTerminal
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * A turn processor responsible for handling the events the agent sends.
 * It validates the events and publishes them to the task's event stream, which persists them to the task storage and
 * delivers them to every stream of the task: the current turn's [Turn.events] and all SubscribeToTask streams.
 *
 * Validation logic attempts to perform basic verification that events follow what is expected from a proper A2A server implementation.
 * These are the main rules:
 *
 * - **Turn type exclusivity**: A turn can only handle either [Message] event or [TaskEvent] events, never both.
 * - **Existing task**: When the turn continues an existing task, the turn handles [TaskEvent] events only: the
 *   agent may send status and artifact updates of the task right away, without sending a [Task] first, and can't
 *   reply with a [Message].
 * - **Context ID validation**: All events must have the same context id as provided [contextId].
 * - **Single message limit**: Only one [Message] can be sent per turn, after which the processor closes.
 * - **Task ID consistency**: [TaskEvent] events must have task ids equal to provided [taskId].
 * - **Terminal state closure**: When the event with the terminal state is sent, processor closes.
 * - **Task replacement**: A [Task] event for a task that already exists is ignored (logged, not persisted, not
 *   delivered), and doesn't close the processor even if its state is terminal. To change an existing task, send status
 *   and artifact updates.
 * - **Turn closure**: When the turn ends, i.e. [ai.koog.a2a.server.agent.AgentExecutor.execute] returns, the processor
 *   of the turn closes.
 *
 * Instances are created by [TurnManager].
 *
 * @property contextId The contextId associated with this turn, representing either an existing context
 * from the incoming request or a newly generated ID that must be used for all events in this turn.
 * @property taskId The taskId associated with this turn, representing either an existing task
 * from the incoming request or a newly generated ID that must be used if creating a new task.
 * Note: This taskId might not correspond to an actually existing task initially - it serves as the
 * identifier that will be validated against all [TaskEvent] in this turn.
 * @param taskExists Whether the task [taskId] already exists when the processor is created, i.e. the processor starts
 * in task mode and rejects [Message] events.
 * @param userMessage The message of the request that starts the turn. It is added to the history of the task, with the
 * task id and context id of this turn set, see [sendTaskEvent]; `null` when the processor isn't bound to a turn.
 */
@OptIn(ExperimentalAtomicApi::class)
public class TurnEventProcessor internal constructor(
    public val contextId: String,
    public val taskId: String,
    private val stream: TaskEventStream,
    private val turnId: Long?,
    private val taskExists: Boolean = false,
    userMessage: Message? = null,
) {
    private companion object {
        private const val TURN_CLOSED = "Turn event processor is closed, can't send events"

        private const val INVALID_CONTEXT_ID = "Event contextId must be same as provided contextId"

        private const val INVALID_TASK_ID = "Event taskId must be same as provided taskId"

        private const val TASK_EVENT_SENT =
            "Task has already been initialized in this turn, only TaskEvent's with the same taskId can be sent from now on"

        private const val TASK_EXISTS =
            "Task already exists, only TaskEvent's with the same taskId can be sent in this turn"
    }

    /**
     * The user message as it is stored in the task history, with the ids of this turn.
     */
    private val storedUserMessage: Message? = userMessage?.copy(taskId = taskId, contextId = contextId)

    /**
     * The user message of a follow-up turn that is not persisted yet, see [sendTaskEvent].
     */
    private var pendingFollowUpMessage: Message? = storedUserMessage.takeIf { taskExists }

    /**
     * The first event the agent sent that was rejected as invalid (by validation or by the storage), if any.
     */
    internal var rejection: Exception? = null
        private set

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

    private val turnMutex = Mutex()

    /**
     * Sends a [Message] to the turn event processor. Validates the message against the turn context and updates
     * the turn state accordingly. The message is delivered only to the current turn's [Turn.events]; it is not
     * part of the task and is not persisted.
     *
     * @param message The message to be sent.
     * @throws [InvalidEventException] for invalid events.
     * @throws [TurnNotActiveException] if the processor is closed.
     * @see TurnEventProcessor
     */
    public suspend fun sendMessage(message: Message): Unit = turnMutex.withLock {
        if (_isOpen.load()) {
            if (taskExists) {
                throw reject(InvalidEventException(TASK_EXISTS))
            }

            if (isTaskEventSent) {
                throw reject(InvalidEventException(TASK_EVENT_SENT))
            }

            if (message.contextId != this.contextId) {
                throw reject(InvalidEventException(INVALID_CONTEXT_ID))
            }

            stream.publish(message, turnId)
            _isOpen.store(false)
        } else {
            throw TurnNotActiveException(TURN_CLOSED)
        }
    }

    /**
     * Sends a [TaskEvent] to the turn event processor.
     * Validates the event against the turn context, persists it to the task storage, and delivers it to all streams
     * of the task. When this method returns, the event is persisted.
     *
     * The server adds the user message of the request to the history of the task, with the task id and context id of
     * the task set, unless the history has a message with the same [Message.messageId] already, so the agent doesn't
     * have to include it: when the agent creates the task with a [Task] event, the message is appended to
     * [Task.history]; when the turn continues an existing task, the message is persisted right before the first
     * status or artifact update the agent sends (and not at all if it sends none), see
     * [TurnManager]. A [Task] event for an existing task is ignored, see [TurnEventProcessor].
     *
     * @param event The event to be sent.
     * @throws [InvalidEventException] for invalid events.
     * @throws [TurnNotActiveException] if the processor is closed.
     * @see TurnEventProcessor
     */
    public suspend fun sendTaskEvent(event: TaskEvent): Unit = send(event, persistFollowUpMessage = true)

    /**
     * Sends a [TaskEvent] originated by the server itself (e.g. the forced cancellation) rather than by the agent:
     * it is not counted as the agent's first event, so it doesn't persist the pending user message of a follow-up.
     */
    internal suspend fun sendServerTaskEvent(event: TaskEvent): Unit = send(event, persistFollowUpMessage = false)

    private suspend fun send(event: TaskEvent, persistFollowUpMessage: Boolean): Unit = turnMutex.withLock {
        if (!_isOpen.load()) {
            throw TurnNotActiveException(TURN_CLOSED)
        }

        isTaskEventSent = true

        if (event.contextId != this.contextId) {
            throw reject(InvalidEventException(INVALID_CONTEXT_ID))
        }

        if (event.taskId != this.taskId) {
            throw reject(InvalidEventException(INVALID_TASK_ID))
        }

        val followUpMessage = pendingFollowUpMessage.takeIf { persistFollowUpMessage && event !is Task }

        val isApplied = try {
            stream.publish(event, turnId, userMessage = storedUserMessage.takeUnless { taskExists }, followUpMessage)
        } catch (e: TaskOperationException) {
            throw reject(e)
        }

        if (isApplied && followUpMessage != null) {
            pendingFollowUpMessage = null
        }

        if (isApplied && event.isTerminal()) {
            _isOpen.store(false)
        }
    }

    private fun <E : Exception> reject(e: E): E {
        if (rejection == null) rejection = e
        return e
    }

    /**
     * Closes the processor: subsequent events are rejected with [TurnNotActiveException]. Waits for an event that
     * is being sent right now to be published.
     */
    internal suspend fun close(): Unit = turnMutex.withLock {
        _isOpen.store(false)
    }

    /**
     * Closes the processor like [close], but without waiting for an event that is being sent right now, which can take
     * as long as the slowest stream of the task needs.
     */
    internal fun closeImmediately() {
        _isOpen.store(false)
    }
}
