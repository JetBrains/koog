package ai.koog.a2a.server.turn

import ai.koog.a2a.annotations.InternalA2AApi
import ai.koog.a2a.exceptions.A2ATaskNotFoundException
import ai.koog.a2a.exceptions.A2AUnsupportedOperationException
import ai.koog.a2a.model.Event
import ai.koog.a2a.model.GetTaskRequest
import ai.koog.a2a.model.SendMessageRequest
import ai.koog.a2a.model.SubscribeToTaskRequest
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskEvent
import ai.koog.a2a.server.notifications.PushNotificationConfigStorage
import ai.koog.a2a.server.notifications.PushNotificationDispatcher
import ai.koog.a2a.server.notifications.PushNotificationSender
import ai.koog.a2a.server.tasks.TaskStorage
import ai.koog.a2a.transport.ServerCallContext
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Manages the turns ([Turn]s) of tasks and the per-task event streams that connect the agent to every stream of a
 * task.
 *
 * ## Turns and task streams
 *
 * - A **turn** is one [ai.koog.a2a.server.agent.AgentExecutor.execute] call, started with [startTurn] and represented
 *   by a [Turn]. Its [Turn.events] only cover that turn.
 * - The **task stream** is an internal, per-task broadcast through which every event of the task is persisted and
 *   delivered, in storage order, to the current turn's view and to every [subscribe] stream. It exists while the task
 *   has a running turn, a [withTaskProcessor] block, or a subscriber, and is recreated from [TaskStorage] when
 *   needed, so idle tasks (e.g. waiting for input) hold no memory.
 *
 * ## Locking
 *
 * - **Turn serialization** is the caller's responsibility: [startTurn] fails if a turn for the same task is already
 *   running, so callers must hold a per-task lock while waiting for [getTurn]`(taskId)?.`[join][Turn.join] and
 *   calling [startTurn], as [ai.koog.a2a.server.A2AServer] does.
 * - The **stream lock** is internal: each task stream persists an event, delivers it to every stream of the task, and
 *   updates its subscriber set under one per-task lock. This gives all streams of a task the same events in the same
 *   order, and makes [subscribe]'s snapshot and registration atomic.
 * - The per-task [TaskEventStream.processorGate] (turn gate) linearizes turn start, turn end, and [withTaskProcessor]
 *   blocks (cancellation): a block either sees the running turn (which can't end while the block runs), or sees no
 *   turn, and then no turn starts until the block is done. A turn of an existing task that would start after a block
 *   left the task in a terminal state is rejected.
 *
 * Lock order: the caller's per-task task lock (see [ai.koog.a2a.server.A2AServer.tasksMutex]), then the turn gate,
 * then either the registry lock or the stream lock. The task lock is never acquired while holding the gate:
 * [withTaskProcessor] blocks (e.g. [ai.koog.a2a.server.A2AServer.onCancelTask]) don't take it, since a caller holding
 * it may be waiting for a running turn to end ([Turn.join]), and a turn can't end without the gate. The registry lock
 * and the stream lock are never nested: the registry lock is held only for short non-suspending bookkeeping and never
 * while acquiring a stream lock, so a stalled task can't block other tasks. The stream lock may be followed by the push
 * queue lock, a leaf lock held only for queue bookkeeping.
 *
 * ## Back-pressure
 *
 * Delivery is synchronous: the agent's `sendTaskEvent`/`sendMessage` returns only after every stream of the task
 * (the requester's [Turn.events], a blocking SendMessage collector, every [subscribe] flow) has accepted the event.
 * Nothing is buffered or dropped, and no collector is disconnected for being slow. A slow collector therefore slows
 * the agent and the other collectors of that task, and operations needing its stream lock ([subscribe], [startTurn],
 * turn end, [withTaskProcessor] publishing) wait behind it. Other tasks, [getTurn], and [activeTurns] never wait.
 *
 * A cancelled collector (e.g. the client went away) stops being a delivery target immediately: the delivery waiting
 * for it fails, the collector is dropped, and the agent and the other collectors go on.
 *
 * ## History
 *
 * The user message of a turn is persisted into [ai.koog.a2a.model.Task.history] by the task stream, deduplicated by
 * [ai.koog.a2a.model.Message.messageId], with its task id and context id set to those of the task:
 * - for a follow-up, lazily, right before the first status or artifact update the agent sends in the turn: the task's
 *   current status message is moved into the history, the user message is appended, and the status message is cleared.
 *   If the agent sends no such event, the user message is not persisted;
 * - for a new task, when the agent sends the initial [ai.koog.a2a.model.Task].
 *
 * An agent's [ai.koog.a2a.model.Task] event for an existing task is ignored.
 *
 * ## Push notifications
 *
 * If [pushConfigStorage] and [pushSender] are configured, every [TaskEvent] persisted to a task
 * (by the agent, or by the server, e.g. a failed or canceled status) is sent as a push notification to every push
 * notification config of the task. [ai.koog.a2a.model.Message] replies, ignored [ai.koog.a2a.model.Task] events, and
 * user messages added to the history are not pushed.
 *
 * Push notifications are queued when the event is persisted and sent in the background, holding no lock, so a slow
 * webhook never delays the agent, the streams, or other tasks. The configs are read when an event is sent. Every config
 * receives the events of its task in storage order. Failed sends are logged and not retried, and pending notifications
 * are dropped when [coroutineScope] is cancelled.
 *
 * @param coroutineScope The scope in which agent jobs, turn monitoring jobs, and push notification jobs are launched.
 * @param taskStorage The storage for tasks.
 * @param pushConfigStorage The storage for push notification configurations.
 * @param pushSender The push notification sender.
 * @param idGenerator The generator of ids of messages created by the server, e.g. the status message of a task marked
 * as failed. Defaults to [UuidIdGenerator].
 */
public class TurnManager(
    private val coroutineScope: CoroutineScope,
    private val taskStorage: TaskStorage,
    pushConfigStorage: PushNotificationConfigStorage? = null,
    pushSender: PushNotificationSender? = null,
    private val idGenerator: IdGenerator = UuidIdGenerator,
) {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    private val pushDispatcher: PushNotificationDispatcher? =
        if (pushConfigStorage != null && pushSender != null) {
            PushNotificationDispatcher(coroutineScope, pushConfigStorage, pushSender)
        } else {
            null
        }

    private class StreamEntry(val stream: TaskEventStream) {
        /** Number of turns, [withTaskProcessor] blocks, and subscribers using the stream. */
        var holders: Int = 0
    }

    /**
     * Guards [streams], [turns], and [nextTurnId]. May be acquired while holding [TaskEventStream.processorGate], but
     * is never held while acquiring a stream lock, which a fan-out may hold for long.
     */
    private val registryMutex = Mutex()
    private val streams = mutableMapOf<String, StreamEntry>()

    /**
     * Running turns by task id. Added once the turn's view is registered and removed once it has ended, both under the
     * task's [TaskEventStream.processorGate].
     */
    private val turns = mutableMapOf<String, Turn>()
    private var nextTurnId = 0L

    /** Completed by [shutdown]; ends every [subscribe] flow without taking any lock. */
    private val shutdownSignal = Job()

    /**
     * Starts a new turn for [RequestContext.taskId]: runs [block] in [coroutineScope] with a [TurnEventProcessor]
     * bound to the task stream.
     *
     * The returned [Turn.events] is registered before [block] starts, so it receives every event of the turn.
     * If the turn continues an existing task ([RequestContext.task] is not `null`):
     * - the processor starts in task mode, see [TurnEventProcessor];
     * - the request's user message is persisted when the agent sends its first status or artifact update, see
     *   "History" above;
     * - the stored task is read atomically with the registration of [Turn.events] (history limited to the request's
     *   [ai.koog.a2a.model.SendMessageConfiguration.historyLength], all artifacts) and kept as the turn's task snapshot;
     * - if the stored task is terminal by then (e.g. cancelled after the caller validated it), the turn isn't started.
     *
     * When [block] completes: if it failed (other than by cancellation) and the task exists in a non-terminal state,
     * the task is marked [ai.koog.a2a.model.TaskState.TASK_STATE_FAILED] and every stream of the task receives that
     * update. Then the processor is closed, [Turn.events] completes, and the turn is removed.
     *
     * Turns of the same task must be serialized by the caller, see [TurnManager].
     *
     * @param context The request context of the turn; its [RequestContext.contextId] and [RequestContext.taskId] are
     * enforced by the processor.
     * @param block The agent logic of the turn.
     * @return The started turn.
     * @throws IllegalStateException if a turn for the same task is already running.
     * @throws A2AUnsupportedOperationException if the turn continues an existing task that is in a terminal state.
     */
    @InternalA2AApi
    public suspend fun startTurn(
        context: RequestContext<SendMessageRequest>,
        block: suspend (TurnEventProcessor) -> Unit,
    ): Turn {
        val taskId = context.taskId
        val contextId = context.contextId
        val taskExists = context.task != null

        // Pinned until the turn has ended (released by monitorTurn), or right away if the turn doesn't start.
        val stream = acquire(taskId)
        var view: ReceiveChannel<Event>? = null
        var started = false

        try {
            /*
             Under the gate no withTaskProcessor block runs, and the turn becomes visible only after its view is
             registered, so a block either finds the turn with its view in place or runs entirely before the turn
             starts (then a terminal state it forced is in the snapshot). The registry lock isn't held in openTurnView,
             which waits for the stream lock.
             */
            return stream.processorGate.withLock {
                val turnId = registryMutex.withLock {
                    check(taskId !in turns) {
                        "Turn for taskId '$taskId' already runs."
                    }

                    nextTurnId++
                }

                val (snapshot, channel) = if (taskExists) {
                    stream.openTurnView(turnId, historyLength = context.params.configuration?.historyLength)
                } else {
                    null to stream.openTurnView(turnId)
                }
                view = channel

                if (snapshot != null && snapshot.status.state.terminal) {
                    throw A2AUnsupportedOperationException(
                        "Task '$taskId' is in terminal state ${snapshot.status.state} and can't accept messages"
                    )
                }

                // The view is registered: from here on, the turn starts even if the caller is cancelled.
                withContext(NonCancellable) {
                    val processor = TurnEventProcessor(
                        contextId = contextId,
                        taskId = taskId,
                        stream = stream,
                        turnId = turnId,
                        taskExists = taskExists,
                        userMessage = context.params.message,
                    )
                    val agentJob = coroutineScope.async(start = CoroutineStart.LAZY) { block(processor) }

                    /*
                     Starts the agent job and ends the turn. It ends the turn under the gate, which is held until the turn
                     is registered below, so it can be launched first. ATOMIC (not LAZY): if coroutineScope is already
                     cancelled (server shutdown), the monitor still runs and ends the turn, so the view completes and join()
                     doesn't hang.
                     */
                    @OptIn(DelicateCoroutinesApi::class)
                    val monitor = coroutineScope.launch(start = CoroutineStart.ATOMIC) {
                        monitorTurn(agentJob, processor, taskId, contextId, stream, turnId)
                    }
                    // The monitor releases the stream from here on.
                    started = true

                    val turn = Turn(
                        taskId = taskId,
                        contextId = contextId,
                        agentJob = agentJob,
                        eventProcessor = processor,
                        view = channel,
                        monitor = monitor,
                        taskSnapshot = snapshot,
                    )

                    registryMutex.withLock { turns[taskId] = turn }

                    turn
                }
            }
        } catch (e: Throwable) {
            // The turn didn't start (once it has, nothing here throws, not even on cancellation): nobody collects the view.
            view?.let(stream::unsubscribe)
            throw e
        } finally {
            if (!started) release(stream)
        }
    }

    /** Waits for the turn's agent job to complete (starting it, if needed), ends the turn, and cleans up. */
    private suspend fun monitorTurn(
        agentJob: Deferred<Unit>,
        processor: TurnEventProcessor,
        taskId: String,
        contextId: String,
        stream: TaskEventStream,
        turnId: Long,
    ) {
        // Fails with a CancellationException if this monitor is cancelled, e.g. on server shutdown.
        val failure = try {
            agentJob.await()
            null
        } catch (e: Throwable) {
            e
        }

        if (failure != null && failure !is CancellationException) {
            // The requester may have got its response already, so the failure must not go unnoticed.
            logger.warn(failure) { "Agent execution failed for taskId='$taskId'" }
        }

        try {
            // The turn is ended even if this monitor is cancelled, so its view completes and the turn is removed.
            withContext(NonCancellable) {
                try {
                    /*
                     Waits for any withTaskProcessor block still publishing through this turn's processor, then closes
                     the processor and the view (endTurn closes it even if it fails) and removes the turn. Removal under
                     the gate means a block never gets the closed processor of an ended turn.
                     */
                    stream.processorGate.withLock {
                        processor.close()

                        try {
                            stream.endTurn(turnId, contextId, failure)
                        } finally {
                            registryMutex.withLock { turns -= taskId }
                        }
                    }
                } finally {
                    release(stream)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Failed to finish the turn of taskId='$taskId'" }
        }
    }

    /**
     * Subscribes to the events of a task (SubscribeToTask).
     *
     * The first event is the task as returned by [TaskStorage.get] (full history and artifacts), followed by every
     * later event of the task across turns. The snapshot is read and the subscription is registered atomically, so no
     * event is missed or repeated. The flow completes when the task reaches a terminal state, or on [shutdown].
     *
     * Errors are thrown on collection, before the first event.
     *
     * @param request The SubscribeToTask request.
     * @param ctx The context of the call, passed to [TaskStorage.get] to scope the lookup.
     * @return A cold flow; each collection is a separate subscription.
     * @throws A2ATaskNotFoundException if the task doesn't exist (or isn't visible to the caller).
     * @throws A2AUnsupportedOperationException if the task is already in a terminal state.
     */
    @InternalA2AApi
    public fun subscribe(request: SubscribeToTaskRequest, ctx: ServerCallContext): Flow<Event> = flow {
        val stream = acquire(request.id)

        try {
            val (snapshot, channel) = stream.subscribe {
                taskStorage.get(GetTaskRequest(id = request.id, tenant = request.tenant), ctx)
            }

            try {
                emit(snapshot)

                while (true) {
                    val event = select<Event?> {
                        // First, so that shutdown wins over a pending event.
                        shutdownSignal.onJoin { null }
                        channel.onReceiveCatching { it.getOrNull() }
                    } ?: break

                    emit(event)
                }
            } finally {
                // Cancels the channel without the stream lock, so a delivery waiting for this collector fails.
                stream.unsubscribe(channel)
            }
        } finally {
            release(stream)
        }
    }

    /**
     * Runs [block] with a [TurnEventProcessor] bound to the task stream of [taskId], e.g. to cancel a task.
     *
     * If a turn of the task is running, [block] receives that turn's processor and agent job, and the turn can't finish
     * until [block] returns, so everything [block] publishes reaches the turn's requester as well. Otherwise, [block]
     * receives a new processor and `null`; the processor is closed when [block] returns (later events are rejected with
     * [ai.koog.a2a.server.exceptions.TurnNotActiveException]). Either way, published events reach every [subscribe]
     * stream of the task.
     *
     * [block] also receives `publish`, which persists and delivers a [TaskEvent] originated by the server (e.g. the
     * forced cancellation) straight to the task stream, bypassing the processor: it isn't validated against the turn,
     * doesn't close the processor, and doesn't persist the running turn's pending user message (see "History" above).
     * It must not be called after [block] returns.
     *
     * No turn of the task starts while [block] runs: a starting turn waits, and is rejected if [block] left the task
     * terminal, see [startTurn].
     *
     * [block] may cancel and join the agent job, but must not wait for the turn to end (e.g. [Turn.join]) or for a turn
     * to start, since neither can happen while [block] runs.
     *
     * @param taskId The task id.
     * @param contextId The context id of the task, used for the new processor when no turn is running.
     * @param block The action to run.
     * @return The result of [block].
     */
    @InternalA2AApi
    public suspend fun <T> withTaskProcessor(
        taskId: String,
        contextId: String,
        block: suspend (
            processor: TurnEventProcessor,
            agentJob: Deferred<Unit>?,
            publish: suspend (TaskEvent) -> Unit,
        ) -> T,
    ): T {
        val stream = acquire(taskId)

        try {
            return stream.processorGate.withLock {
                val turn = registryMutex.withLock { turns[taskId] }

                val processor = turn?.eventProcessor ?: TurnEventProcessor(
                    contextId = contextId,
                    taskId = taskId,
                    stream = stream,
                    turnId = null,
                    // The task exists: a Message is rejected like in any turn of an existing task.
                    taskExists = true,
                )

                var isBlockRunning = true
                val publish: suspend (TaskEvent) -> Unit = { event ->
                    check(isBlockRunning) { "Can't publish after the withTaskProcessor block has returned" }
                    require(event.taskId == taskId) { "Event of task '${event.taskId}' can't be published to task '$taskId'" }
                    stream.publish(event, turnId = null)
                }

                try {
                    block(processor, turn?.agentJob, publish)
                } finally {
                    isBlockRunning = false
                    // Nothing may publish through a standalone processor once the stream may be dropped. Closed without
                    // waiting for a send in progress, which could hold the gate for long.
                    if (turn == null) processor.closeImmediately()
                }
            }
        } finally {
            release(stream)
        }
    }

    /**
     * Ends every [subscribe] flow, current and future, normally (without an error), e.g. on server shutdown, including
     * those of idle tasks that no turn would ever close. Doesn't stop turns: their views end when the turns end, e.g.
     * when [coroutineScope] is cancelled. Takes no lock, so it never waits for a stalled task.
     */
    @InternalA2AApi
    public fun shutdown() {
        shutdownSignal.complete()
    }

    /**
     * Returns the running turn of the task, if any. A turn still being started by [startTurn] isn't reported until its
     * [Turn.events] is registered.
     */
    public suspend fun getTurn(taskId: String): Turn? = registryMutex.withLock {
        turns[taskId]
    }

    /** Returns the number of running turns. */
    public suspend fun activeTurns(): Int = registryMutex.withLock {
        turns.size
    }

    /** Returns the number of task streams currently held in memory. */
    internal suspend fun activeStreams(): Int = registryMutex.withLock {
        streams.size
    }

    private suspend fun acquire(taskId: String): TaskEventStream = registryMutex.withLock {
        val entry = streams.getOrPut(taskId) {
            val onPersisted: (suspend (TaskEvent) -> Unit)? = pushDispatcher?.let { dispatcher ->
                { event -> dispatcher.enqueue(taskId, event) }
            }
            StreamEntry(TaskEventStream(taskId, taskStorage, idGenerator, onPersisted))
        }
        entry.holders++

        entry.stream
    }

    private suspend fun release(stream: TaskEventStream): Unit = withContext(NonCancellable) {
        registryMutex.withLock {
            val entry = streams[stream.taskId]

            if (entry != null && entry.stream === stream) {
                entry.holders--

                if (entry.holders == 0) {
                    streams -= stream.taskId
                }
            }
        }
    }
}
