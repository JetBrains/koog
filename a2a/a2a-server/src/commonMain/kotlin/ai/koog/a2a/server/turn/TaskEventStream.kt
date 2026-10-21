package ai.koog.a2a.server.turn

import ai.koog.a2a.exceptions.A2ATaskNotFoundException
import ai.koog.a2a.exceptions.A2AUnsupportedOperationException
import ai.koog.a2a.model.Event
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskEvent
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatus
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.server.tasks.TaskStorage
import ai.koog.a2a.server.tasks.isTerminal
import ai.koog.a2a.server.tasks.plusIfAbsent
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * Per-task event stream: the single place where events of one task are persisted and broadcast.
 *
 * One instance exists per task while it has a running turn, an active [TurnEventProcessor] (e.g. during
 * cancellation), or a subscriber; [TurnManager] creates it lazily and drops it when none remain. The stream keeps no
 * task state: every read goes to [taskStorage], so a dropped stream can be recreated at any time.
 *
 * Every operation runs under [mutex] (the "stream lock"), which guards the storage write, the fan-out, and the
 * subscriber set together. As a result:
 * - all subscribers observe events in the order they were persisted;
 * - [subscribe] reads the snapshot and registers the subscriber atomically, so a subscriber neither misses nor
 *   duplicates an event that is persisted concurrently.
 *
 * Delivery is back-pressured: every subscriber has its own rendezvous channel, and the fan-out suspends, still under
 * the stream lock, until each subscriber has accepted the event. A slow subscriber therefore slows the publisher, the
 * other subscribers, and any operation waiting for the stream lock ([subscribe], [openTurnView], [endTurn], other
 * publishers); no event is dropped and no subscriber is evicted.
 *
 * A subscriber whose consumer has gone away must cancel its channel (see [unsubscribe]). Cancelling doesn't need the
 * stream lock, so it works even while the fan-out is suspended on that subscriber: the pending delivery fails and the
 * fan-out drops the subscriber and goes on. If the publisher itself is cancelled mid-fan-out, the event is already
 * persisted but the subscribers not reached yet don't receive it. A terminal event still closes every subscriber, so
 * none of them waits for an event that will never come.
 *
 * Every persisted [TaskEvent] is passed to [onPersisted] right after it is stored, before the fan-out and still under
 * the stream lock, so its calls follow the storage order. Storing and reporting an event isn't interrupted by the
 * publisher's cancellation, so every stored event is reported. User messages added to the history are not events and
 * aren't passed.
 *
 * ## History
 *
 * The stream adds user messages to [Task.history] (deduplicated by [Message.messageId], see [plusIfAbsent]), so
 * agents don't have to:
 * - for a new task, [publish] adds the turn's user message to the agent's initial [Task];
 * - for a follow-up, [publish] adds it lazily, right before applying the turn's first non-[Task] [TaskEvent]: the
 *   current [ai.koog.a2a.model.TaskStatus.message] is moved into the history, the user message is appended, and the
 *   status message is cleared. A turn with no such event doesn't persist the user message.
 *
 * A [Task] event for an existing task is ignored by [publish]: neither stored nor delivered.
 *
 * There are two kinds of subscribers:
 * - task subscribers (SubscribeToTask) receive every [TaskEvent] and end only at a terminal state;
 * - the turn view receives every [TaskEvent] plus the turn's [ai.koog.a2a.model.Message], and ends at a terminal state
 *   or when [endTurn] is called. Closing the view under the stream lock after the turn's last event is the in-band
 *   end-of-turn marker. Turns of a task don't overlap and each ends before the next one opens its view, so there is
 *   at most one turn view at a time.
 *
 * @param taskId the id of the task this stream belongs to
 * @param taskStorage the storage where task events are persisted
 * @param idGenerator generates the id of the message attached to the failure status
 * @param onPersisted called with every persisted [TaskEvent] (e.g. to queue push notifications), non-cancellable and
 * under the stream lock, so it must return quickly
 */
internal class TaskEventStream(
    val taskId: String,
    private val taskStorage: TaskStorage,
    private val idGenerator: IdGenerator,
    private val onPersisted: (suspend (TaskEvent) -> Unit)? = null,
) {
    private companion object {
        private val logger = KotlinLogging.logger {}

        private const val FAILURE_MESSAGE = "Task failed due to an internal error"
    }

    private class Subscriber(
        val channel: Channel<Event>,
        val isTurnView: Boolean,
    )

    /** The stream lock: guards [taskStorage] writes for this task, fan-out, and [subscribers]. */
    private val mutex = Mutex()

    private val subscribers = mutableListOf<Subscriber>()

    /**
     * Held by [TurnManager] while a processor from [TurnManager.withTaskProcessor] is in use, a turn is starting, or a
     * turn is finishing. Keeps a turn from finishing while another party (e.g. a cancellation handler) still publishes
     * through its processor, and from starting while such a party decides on the task's state.
     */
    val processorGate: Mutex = Mutex()

    /**
     * Atomically reads the current task with [lookup] and registers a task subscriber.
     *
     * @param lookup reads the current task from the storage; called under the stream lock
     * @return the snapshot and the channel delivering every subsequent event of the task
     * @throws A2ATaskNotFoundException if [lookup] returns `null`
     * @throws A2AUnsupportedOperationException if the task is in a terminal state
     */
    suspend fun subscribe(lookup: suspend () -> Task?): Pair<Task, ReceiveChannel<Event>> = mutex.withLock {
        val task = lookup() ?: throw A2ATaskNotFoundException("Task '$taskId' not found")

        if (task.status.state.terminal) {
            throw A2AUnsupportedOperationException(
                "Task '$taskId' is in terminal state ${task.status.state} and can't be subscribed to"
            )
        }

        task to register(isTurnView = false)
    }

    /**
     * Registers the turn view. Must be called before the turn publishes its first event, and after the previous turn
     * of the task has ended.
     */
    suspend fun openTurnView(): ReceiveChannel<Event> = mutex.withLock {
        register(isTurnView = true)
    }

    /**
     * Stops the subscriber reading from [channel] by cancelling it without the stream lock, so it never waits for the
     * fan-out and a suspended delivery to [channel] fails immediately. The subscriber is removed by the next fan-out,
     * registration, or terminal event.
     */
    fun unsubscribe(channel: ReceiveChannel<Event>) {
        channel.cancel()
    }

    /**
     * Publishes [event]: a [TaskEvent] is persisted and delivered to all subscribers, a
     * [ai.koog.a2a.model.Message] only to the turn view. A terminal [TaskEvent] closes all subscribers.
     *
     * A [Task] for an existing task is ignored. A [Task] for a new task is persisted with [userMessage] added to its
     * history, unless it has it already. A [TaskEvent] other than a [Task] is preceded, if [followUpMessage] is set, by
     * persisting [followUpMessage] into the stored task, see "History" above.
     *
     * Returns once the event is persisted and every addressed subscriber has accepted it, however long a slow
     * subscriber takes.
     *
     * @param event the event to publish
     * @param userMessage the message of the request that started the turn, if the task is created by this turn
     * @param followUpMessage the message of the request that started the turn, if it continues the task and hasn't
     * been persisted yet
     * @return `false` if the event was ignored, `true` otherwise
     */
    suspend fun publish(
        event: Event,
        userMessage: Message? = null,
        followUpMessage: Message? = null,
    ): Boolean = mutex.withLock {
        when (event) {
            is Task -> {
                if (taskStorage.get(taskId, historyLength = 0, includeArtifacts = false) != null) {
                    logger.warn {
                        "Ignoring Task event for taskId='$taskId': the task already exists, send status and artifact " +
                            "updates to change it"
                    }
                    return@withLock false
                }

                val created = if (userMessage == null) {
                    event
                } else {
                    event.copy(history = event.history.plusIfAbsent(userMessage))
                }

                persistAndDeliver(created)
            }

            is TaskEvent -> {
                if (followUpMessage != null) {
                    persistUserMessage(followUpMessage)
                }

                persistAndDeliver(event)
            }

            else -> fanOut(event) { it.isTurnView }
        }

        true
    }

    private suspend fun persistAndDeliver(event: TaskEvent) {
        persist(event)

        try {
            fanOut(event) { true }
        } finally {
            // Even if the publisher is cancelled mid-fan-out: the task is terminal, so nothing else would close them.
            if (event.isTerminal()) {
                closeAll()
            }
        }
    }

    /**
     * Stores [event] and reports it to [onPersisted]. Must be called under the stream lock. Not cancellable: the
     * storage may commit the write and still throw on return to a cancelled publisher, and the stored event must be
     * reported anyway.
     */
    private suspend fun persist(event: TaskEvent): Unit = withContext(NonCancellable) {
        taskStorage.update(event)
        onPersisted?.invoke(event)
    }

    /** Adds [userMessage] to the history of the stored task, see [publish]. Must be called under the stream lock. */
    private suspend fun persistUserMessage(userMessage: Message) {
        val task = taskStorage.get(taskId, historyLength = null, includeArtifacts = true) ?: return

        if (task.history.orEmpty().any { it.messageId == userMessage.messageId }) return

        val previousStatusMessage = task.status.message
        val history = (previousStatusMessage?.let { task.history.plusIfAbsent(it) } ?: task.history)
            .plusIfAbsent(userMessage)

        taskStorage.update(task.copy(history = history, status = task.status.copy(message = null)))
    }

    /**
     * Ends the turn with [outcome]: closes the turn view after the last event of the turn.
     *
     * If the agent failed ([TurnOutcome.Failed], [TurnOutcome.Rejected]) and the task exists in a non-terminal state,
     * marks the task [TaskState.TASK_STATE_FAILED] and delivers that update to all subscribers, which are then closed.
     * For [TurnOutcome.Rejected], the view is closed first, so the turn's requester doesn't receive that update. For
     * [TurnOutcome.RequestError], the task is left unchanged.
     *
     * @param contextId the context id of the task, used for the failure status update
     * @param outcome how the turn ended
     */
    suspend fun endTurn(contextId: String, outcome: TurnOutcome): Unit = mutex.withLock {
        try {
            when (outcome) {
                is TurnOutcome.Completed, is TurnOutcome.RequestError -> Unit
                is TurnOutcome.Failed -> markFailed(contextId)
                is TurnOutcome.Rejected -> {
                    closeTurnView()
                    markFailed(contextId)
                }
            }
        } finally {
            closeTurnView()
        }
    }

    private suspend fun markFailed(contextId: String) {
        val task = taskStorage.get(taskId, historyLength = 0, includeArtifacts = false)

        if (task != null && !task.status.state.terminal) {
            val taskContextId = task.contextId ?: contextId
            val failed = TaskStatusUpdateEvent(
                taskId = taskId,
                contextId = taskContextId,
                status = TaskStatus(
                    state = TaskState.TASK_STATE_FAILED,
                    // Deliberately generic: exception details must not leak to clients.
                    message = Message(
                        messageId = idGenerator.generateMessageId(taskId, taskContextId),
                        role = Role.ROLE_AGENT,
                        parts = listOf(TextPart(FAILURE_MESSAGE)),
                        contextId = taskContextId,
                        taskId = taskId,
                    ),
                    timestamp = Clock.System.now(),
                ),
            )

            persistAndDeliver(failed)
        }
    }

    private fun closeTurnView() {
        subscribers.removeAll { subscriber ->
            subscriber.isTurnView.also { if (it) subscriber.channel.close() }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun register(isTurnView: Boolean): ReceiveChannel<Event> {
        // Drop subscribers whose consumers have gone away.
        subscribers.removeAll { it.channel.isClosedForSend }

        check(!isTurnView || subscribers.none { it.isTurnView }) { "Task '$taskId' already has a turn view" }

        val channel = Channel<Event>(Channel.RENDEZVOUS)
        subscribers += Subscriber(channel, isTurnView)
        return channel
    }

    private suspend fun fanOut(event: Event, filter: (Subscriber) -> Boolean) {
        val iterator = subscribers.iterator()

        while (iterator.hasNext()) {
            val subscriber = iterator.next()
            if (!filter(subscriber)) continue

            if (!subscriber.channel.deliver(event)) {
                // The consumer has gone away, e.g. its collector was cancelled.
                iterator.remove()
            }
        }
    }

    /**
     * Suspends until the consumer accepts [event].
     *
     * @return `false` if the channel is closed or cancelled, i.e. nobody will ever receive from it
     * @throws CancellationException if the calling coroutine is cancelled
     */
    private suspend fun Channel<Event>.deliver(event: Event): Boolean = try {
        send(event)
        true
    } catch (e: CancellationException) {
        // A channel cancelled by its consumer also fails send() with a CancellationException: tell it apart from
        // the publisher's own cancellation.
        currentCoroutineContext().ensureActive()
        false
    } catch (e: ClosedSendChannelException) {
        false
    }

    /** Doesn't suspend, so it is safe in a `finally` of a cancelled publisher. */
    private fun closeAll() {
        subscribers.forEach { it.channel.close() }
        subscribers.clear()
    }
}
