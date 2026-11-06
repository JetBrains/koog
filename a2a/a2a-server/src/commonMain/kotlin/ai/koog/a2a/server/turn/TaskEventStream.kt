package ai.koog.a2a.server.turn

import ai.koog.a2a.exceptions.A2AInternalErrorException
import ai.koog.a2a.exceptions.A2ATaskNotFoundException
import ai.koog.a2a.exceptions.A2AUnsupportedOperationException
import ai.koog.a2a.exceptions.ErrorInfo
import ai.koog.a2a.model.Event
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskEvent
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatus
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.server.StreamDeliveryConfig
import ai.koog.a2a.server.tasks.STATUS_ONLY
import ai.koog.a2a.server.tasks.TaskStorage
import ai.koog.a2a.server.tasks.isTerminal
import ai.koog.a2a.server.tasks.plusIfAbsent
import ai.koog.a2a.transport.ServerCallContext
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Clock

/**
 * Per-task event stream: the single place where events of one task are persisted and broadcast.
 *
 * One instance exists per task while it has a running turn, an active [TurnEventProcessor] (e.g. during
 * cancellation), or a subscriber; [TurnManager] creates it lazily and drops it when none remain. The stream keeps no
 * task state: every read goes to [taskStorage], so a dropped stream can be recreated at any time.
 *
 * The stream is shared by every turn and caller of the task, so it has no call context of its own: every storage read
 * and write is made with the [ServerCallContext] passed to [publish] or [endTurn], that of the turn or the
 * cancellation it is made for.
 *
 * Every operation runs under [mutex] (the "stream lock"), which guards the storage write, the fan-out, and the
 * subscriber set together. As a result:
 * - all subscribers observe events in the order they were persisted;
 * - [subscribe] reads the snapshot and registers the subscriber atomically, so a subscriber neither misses nor
 *   duplicates an event that is persisted concurrently.
 *
 * ## Delivery
 *
 * Every subscriber has its own queue, sized and handled as its [StreamDeliveryConfig] says, and the fan-out runs
 * under the stream lock: it returns once the event is in every addressed queue, so it may wait for slow readers.
 * Queues with room take the event without suspending. A full [StreamDeliveryConfig.Suspend] queue is waited for, so
 * its slow reader slows the publisher, the other subscribers, and any operation waiting for the stream lock
 * ([subscribe], [openTurnView], [endTurn], other publishers). Full [StreamDeliveryConfig.Evict] queues are waited for
 * together, each at most its timeout, and those still full then are evicted: closed with [slowConsumerError] after
 * their queued events, and removed. Closing keeps the queued events, so the in-band end-of-turn marker and the close
 * at a terminal event still come after every event before them.
 *
 * A subscriber whose consumer has gone away must cancel its channel (see [unsubscribe]); on shutdown, [TurnManager]
 * ends every subscriber with [Subscriber.endOnShutdown]. Neither needs the stream lock, so they work even while the
 * fan-out waits for that subscriber: the pending delivery fails and the fan-out drops the subscriber and goes on. If
 * the publisher itself is cancelled mid-fan-out, the event is already persisted but the subscribers not reached yet
 * don't receive it. A terminal event still closes every subscriber, so none of them waits for an event that will never
 * come.
 *
 * ## Push notifications
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
 * - for a follow-up, [publish] adds it lazily with [TaskStorage.appendHistory], right before applying the turn's first
 *   non-[Task] [TaskEvent]: the current [ai.koog.a2a.model.TaskStatus.message] is moved into the history, the user
 *   message is appended, and the status message is cleared. A turn with no such event doesn't persist the user
 *   message.
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
 * @param idGenerator generates the id of the generic failure status message
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
    }

    /**
     * A task subscriber or the turn view, reading the events delivered to it from [events].
     *
     * It is ended once, by whichever comes first: the task stream ([close]: at a terminal event, at the end of the
     * turn, or on eviction), which keeps the queued events, or the shutdown ([endOnShutdown]). So a shutdown never
     * drops the queued events of a subscriber that was ended before, e.g. a buffered terminal event. Its consumer may
     * cancel [events] at any time.
     *
     * @property delivery how events are queued for it
     * @property isTurnView whether it is the turn view
     */
    @OptIn(ExperimentalAtomicApi::class)
    internal class Subscriber(val delivery: StreamDeliveryConfig, val isTurnView: Boolean) {
        private val channel = Channel<Event>(delivery.capacity)

        private val isEnded = AtomicBoolean(false)

        /** The events delivered to this subscriber. */
        val events: ReceiveChannel<Event> get() = channel

        /** Whether nothing can be delivered any more, e.g. its consumer has cancelled [events]. */
        @OptIn(DelicateCoroutinesApi::class)
        val isClosed: Boolean get() = channel.isClosedForSend

        /** Ends the delivery: [events] yields the queued events, then completes, or fails with [cause] if set. */
        fun close(cause: Throwable? = null) {
            if (isEnded.compareAndSet(expectedValue = false, newValue = true)) channel.close(cause)
        }

        /**
         * Ends the subscriber with [ServerShutdownException], unless it has been ended already: after its queued
         * events if [isDrained], else at once, dropping them, so no delivery waits for its reader any more. Lock-free.
         */
        fun endOnShutdown(isDrained: Boolean) {
            if (isEnded.compareAndSet(expectedValue = false, newValue = true)) {
                if (isDrained) channel.close(ServerShutdownException()) else channel.cancel(ServerShutdownException())
            }
        }

        fun trySend(event: Event): ChannelResult<Unit> = channel.trySend(event)

        /**
         * Suspends until the consumer accepts [event].
         *
         * @return `false` if the channel is closed or cancelled, i.e. nobody will ever receive from it
         * @throws CancellationException if the calling coroutine is cancelled
         */
        suspend fun deliver(event: Event): Boolean = try {
            channel.send(event)
            true
        } catch (e: CancellationException) {
            // A channel cancelled by its consumer also fails send() with a CancellationException: tell it apart from
            // the publisher's own cancellation.
            currentCoroutineContext().ensureActive()
            false
        } catch (e: ClosedSendChannelException) {
            false
        }
    }

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
     * @param delivery how events are queued for the subscriber
     * @param lookup reads the current task from the storage; called under the stream lock
     * @return the snapshot and the subscriber receiving every subsequent event of the task
     * @throws A2ATaskNotFoundException if [lookup] returns `null`
     * @throws A2AUnsupportedOperationException if the task is in a terminal state
     */
    suspend fun subscribe(
        delivery: StreamDeliveryConfig,
        lookup: suspend () -> Task?,
    ): Pair<Task, Subscriber> = mutex.withLock {
        val task = lookup() ?: throw A2ATaskNotFoundException("Task '$taskId' not found")

        if (task.status.state.terminal) {
            throw A2AUnsupportedOperationException(
                "Task '$taskId' is in terminal state ${task.status.state} and can't be subscribed to"
            )
        }

        task to register(delivery, isTurnView = false)
    }

    /**
     * Registers the turn view. Must be called before the turn publishes its first event, and after the previous turn
     * of the task has ended.
     *
     * @param delivery how events are queued for the view
     */
    suspend fun openTurnView(delivery: StreamDeliveryConfig): Subscriber = mutex.withLock {
        register(delivery, isTurnView = true)
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
     * Returns once the event is persisted and queued for every addressed subscriber, or that subscriber is evicted, see
     * "Delivery" above.
     *
     * @param event the event to publish
     * @param callContext the context of the turn or cancellation the event is published for, passed to every storage
     * call
     * @param userMessage the message of the request that started the turn, if the task is created by this turn
     * @param followUpMessage the message of the request that started the turn, if it continues the task and hasn't
     * been persisted yet
     * @return `false` if the event was ignored, `true` otherwise
     */
    suspend fun publish(
        event: Event,
        callContext: ServerCallContext,
        userMessage: Message? = null,
        followUpMessage: Message? = null,
    ): Boolean = mutex.withLock {
        when (event) {
            is Task -> {
                if (read(callContext) != null) {
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

                persistAndDeliver(created, callContext)
            }

            is TaskEvent -> {
                if (followUpMessage != null) {
                    taskStorage.appendHistory(taskId, followUpMessage, callContext)
                }

                persistAndDeliver(event, callContext)
            }

            else -> fanOut(event) { it.isTurnView }
        }

        true
    }

    /** Reads the task's status, scoped to [callContext]. */
    private suspend fun read(callContext: ServerCallContext): Task? = taskStorage.get(taskId, STATUS_ONLY, callContext)

    private suspend fun persistAndDeliver(event: TaskEvent, callContext: ServerCallContext) {
        persist(event, callContext)

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
    private suspend fun persist(event: TaskEvent, callContext: ServerCallContext): Unit = withContext(NonCancellable) {
        taskStorage.update(event, callContext)
        onPersisted?.invoke(event)
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
     * @param callContext the context of the turn, passed to every storage call
     * @param failureMessage the status message of the failure update, with its task id, context id, and role replaced
     * by the task's and [Role.ROLE_AGENT]; `null` for the generic one, see [genericFailureMessage]
     */
    suspend fun endTurn(
        contextId: String,
        outcome: TurnOutcome,
        callContext: ServerCallContext,
        failureMessage: Message? = null,
    ): Unit = mutex.withLock {
        try {
            when (outcome) {
                is TurnOutcome.Completed, is TurnOutcome.RequestError -> Unit
                is TurnOutcome.Failed -> markFailed(contextId, callContext, failureMessage)
                is TurnOutcome.Rejected -> {
                    closeTurnView()
                    markFailed(contextId, callContext, failureMessage)
                }
            }
        } finally {
            closeTurnView()
        }
    }

    private suspend fun markFailed(contextId: String, callContext: ServerCallContext, failureMessage: Message?) {
        val task = read(callContext)

        if (task != null && !task.status.state.terminal) {
            val taskContextId = task.contextId ?: contextId
            val message = failureMessage?.copy(taskId = taskId, contextId = taskContextId, role = Role.ROLE_AGENT)
                ?: idGenerator.genericFailureMessage(taskId, taskContextId)
            val failed = TaskStatusUpdateEvent(
                taskId = taskId,
                contextId = taskContextId,
                status = TaskStatus(
                    state = TaskState.TASK_STATE_FAILED,
                    message = message,
                    timestamp = Clock.System.now(),
                ),
            )

            persistAndDeliver(failed, callContext)
        }
    }

    private fun closeTurnView() {
        subscribers.removeAll { subscriber ->
            subscriber.isTurnView.also { if (it) subscriber.close() }
        }
    }

    private fun register(delivery: StreamDeliveryConfig, isTurnView: Boolean): Subscriber {
        // Drop subscribers whose consumers have gone away.
        subscribers.removeAll { it.isClosed }

        check(!isTurnView || subscribers.none { it.isTurnView }) { "Task '$taskId' already has a turn view" }

        return Subscriber(delivery, isTurnView).also { subscribers += it }
    }

    /**
     * Delivers [event] to the subscribers matching [filter], see "Delivery" above. Every subscriber gets at most this
     * event in flight, so each one receives the events in publication order.
     */
    private suspend fun fanOut(event: Event, filter: (Subscriber) -> Boolean) {
        val full = mutableListOf<Subscriber>()
        val iterator = subscribers.iterator()

        while (iterator.hasNext()) {
            val subscriber = iterator.next()
            if (!filter(subscriber)) continue

            val isOpen = when (subscriber.delivery) {
                is StreamDeliveryConfig.Suspend -> subscriber.deliver(event)

                is StreamDeliveryConfig.Evict -> {
                    val result = subscriber.trySend(event)
                    if (!result.isSuccess && !result.isClosed) full += subscriber
                    !result.isClosed
                }
            }

            // The consumer has gone away, e.g. its collector was cancelled.
            if (!isOpen) iterator.remove()
        }

        if (full.isNotEmpty()) awaitOrEvict(event, full)
    }

    /**
     * Waits for the full queues of [full] concurrently, so the deadline is shared: publishing [event] waits at most
     * the longest timeout among them, not their sum. Those still full at their deadline are evicted.
     */
    private suspend fun awaitOrEvict(event: Event, full: List<Subscriber>) {
        val timeouts = full.map { (it.delivery as StreamDeliveryConfig.Evict).timeout }
        val results = coroutineScope {
            full.zip(timeouts) { subscriber, timeout ->
                async { withTimeoutOrNull(timeout) { subscriber.deliver(event) } }
            }.awaitAll()
        }

        full.indices.forEach { index ->
            val subscriber = full[index]
            when (results[index]) {
                true -> Unit

                false -> subscribers.remove(subscriber)

                null -> {
                    logger.warn {
                        "Evicting a stream of taskId='$taskId': its reader hasn't taken an event for ${timeouts[index]} " +
                            "with ${subscriber.delivery.capacity} events queued"
                    }
                    subscriber.close(slowConsumerError(taskId))
                    subscribers.remove(subscriber)
                }
            }
        }
    }

    /** Doesn't suspend, so it is safe in a `finally` of a cancelled publisher. */
    private fun closeAll() {
        subscribers.forEach { it.close() }
        subscribers.clear()
    }
}

/**
 * The error an evicted subscriber fails with after its queued events, see [StreamDeliveryConfig.Evict]. Not a
 * [CancellationException], so the transport sends it to the client as the stream's last event.
 */
internal fun slowConsumerError(taskId: String): A2AInternalErrorException = A2AInternalErrorException(
    message = "Stream closed because the client is not reading events fast enough; resubscribe with SubscribeToTask " +
        "or call GetTask",
    details = listOf(ErrorInfo(reason = SLOW_CONSUMER_REASON, metadata = mapOf("taskId" to taskId))),
)

/** The [ErrorInfo.reason] of [slowConsumerError]. */
internal const val SLOW_CONSUMER_REASON = "SLOW_CONSUMER"

/**
 * The status message of a task marked failed by the server when no custom one is given. Deliberately generic:
 * exception details must not leak to clients.
 */
internal suspend fun IdGenerator.genericFailureMessage(taskId: String, contextId: String): Message = Message(
    messageId = generateMessageId(taskId, contextId),
    role = Role.ROLE_AGENT,
    parts = listOf(TextPart("Task failed due to an internal error")),
    contextId = contextId,
    taskId = taskId,
)
