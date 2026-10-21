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
 * Validates the events the agent sends and publishes them to the task's event stream, which persists them and
 * delivers them to every stream of the task: the current turn's [Turn.events] and all SubscribeToTask streams.
 *
 * Rules:
 * - **Turn type exclusivity**: a turn handles either a [Message] or [TaskEvent]s, never both.
 * - **Existing task**: when the turn continues an existing task, only [TaskEvent]s are accepted: the agent may send
 *   status and artifact updates right away, without a [Task] first, and can't reply with a [Message].
 * - **Context ID / task ID**: all events must have the processor's [contextId], and [TaskEvent]s its [taskId].
 * - **Single message**: only one [Message] per turn, after which the processor closes.
 * - **Terminal state**: an event with a terminal state closes the processor.
 * - **Task replacement**: a [Task] event for an existing task is ignored (logged, not persisted, not delivered) and
 *   doesn't close the processor even if terminal. To change an existing task, send status and artifact updates.
 * - **Turn end**: when [ai.koog.a2a.server.agent.AgentExecutor.execute] returns, the processor closes.
 *
 * Send only from within the [ai.koog.a2a.server.agent.AgentExecutor.execute] or
 * [ai.koog.a2a.server.agent.AgentExecutor.cancel] call the processor was passed to (including its child coroutines).
 * Sending from elsewhere, e.g. through a processor kept after that call, is unsupported: such an event may still be
 * delivered after the processor has closed.
 *
 * Instances are created by [TurnManager].
 *
 * @property contextId The context id of this turn: the incoming request's or a generated one.
 * @property taskId The task id of this turn: the incoming request's or a generated one for a new task. The task might
 * not exist yet; this id is what all [TaskEvent]s of the turn are validated against.
 * @param taskExists Whether the task [taskId] already exists when the processor is created, i.e. the processor starts
 * in task mode and rejects [Message] events.
 * @param userMessage The message of the request that starts the turn, added to the task history with this turn's task
 * id and context id, see [sendTaskEvent]; `null` when the processor isn't bound to a turn.
 * @param onRejected Called with the exception of every event the processor rejects as invalid.
 */
@OptIn(ExperimentalAtomicApi::class)
public class TurnEventProcessor internal constructor(
    public val contextId: String,
    public val taskId: String,
    private val stream: TaskEventStream,
    private val turnId: Long?,
    private val taskExists: Boolean = false,
    userMessage: Message? = null,
    private val onRejected: (Exception) -> Unit = {},
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

    /** The user message as stored in the task history, with the ids of this turn. */
    private val storedUserMessage: Message? = userMessage?.copy(taskId = taskId, contextId = contextId)

    /** The user message of a follow-up turn that is not persisted yet, see [sendTaskEvent]. */
    private var pendingFollowUpMessage: Message? = storedUserMessage.takeIf { taskExists }

    init {
        require(stream.taskId == taskId) { "Event stream of task '${stream.taskId}' can't be used for task '$taskId'" }
    }

    /** Atomic since [close] writes it without [turnMutex]. */
    private val isOpen: AtomicBoolean = AtomicBoolean(true)

    /** Whether a task event was sent through this processor, after which [Message] events are rejected. */
    private var isTaskEventSent: Boolean = false

    private val turnMutex = Mutex()

    /**
     * Sends a [Message]. Validates it against the turn context and closes the processor. The message is delivered only
     * to the current turn's [Turn.events]; it is not part of the task and is not persisted.
     *
     * @param message The message to be sent.
     * @throws [InvalidEventException] for invalid events.
     * @throws [TurnNotActiveException] if the processor is closed.
     * @see TurnEventProcessor
     */
    public suspend fun sendMessage(message: Message): Unit = turnMutex.withLock {
        if (isOpen.load()) {
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
            isOpen.store(false)
        } else {
            throw TurnNotActiveException(TURN_CLOSED)
        }
    }

    /**
     * Sends a [TaskEvent]. Validates it against the turn context, persists it, and delivers it to all streams of the
     * task. When this method returns, the event is persisted.
     *
     * The server adds the request's user message to the task history (unless it has that [Message.messageId] already),
     * so the agent needn't: appended to [Task.history] when the agent creates the task, or persisted right before the
     * first status or artifact update for an existing task (not at all if none is sent), see [TurnManager]. A [Task]
     * event for an existing task is ignored, see [TurnEventProcessor].
     *
     * @param event The event to be sent.
     * @throws [InvalidEventException] for invalid events.
     * @throws [TurnNotActiveException] if the processor is closed.
     * @see TurnEventProcessor
     */
    public suspend fun sendTaskEvent(event: TaskEvent): Unit = turnMutex.withLock {
        if (!isOpen.load()) {
            throw TurnNotActiveException(TURN_CLOSED)
        }

        isTaskEventSent = true

        if (event.contextId != this.contextId) {
            throw reject(InvalidEventException(INVALID_CONTEXT_ID))
        }

        if (event.taskId != this.taskId) {
            throw reject(InvalidEventException(INVALID_TASK_ID))
        }

        val followUpMessage = pendingFollowUpMessage.takeIf { event !is Task }

        val isApplied = try {
            stream.publish(event, turnId, userMessage = storedUserMessage.takeUnless { taskExists }, followUpMessage)
        } catch (e: TaskOperationException) {
            throw reject(e)
        }

        if (isApplied && followUpMessage != null) {
            pendingFollowUpMessage = null
        }

        if (isApplied && event.isTerminal()) {
            isOpen.store(false)
        }
    }

    private fun <E : Exception> reject(e: E): E = e.also(onRejected)

    /**
     * Closes the processor: later events are rejected with [TurnNotActiveException]. Doesn't wait for an event being
     * sent, see [TurnEventProcessor] for where sending is supported.
     */
    internal fun close() {
        isOpen.store(false)
    }
}
