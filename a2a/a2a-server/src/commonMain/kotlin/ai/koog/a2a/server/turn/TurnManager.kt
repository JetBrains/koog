package ai.koog.a2a.server.turn

import ai.koog.a2a.annotations.InternalA2AApi
import ai.koog.a2a.exceptions.A2AInvalidParamsException
import ai.koog.a2a.exceptions.A2ATaskNotFoundException
import ai.koog.a2a.exceptions.A2AUnsupportedOperationException
import ai.koog.a2a.model.Event
import ai.koog.a2a.model.GetTaskRequest
import ai.koog.a2a.model.SendMessageRequest
import ai.koog.a2a.model.SubscribeToTaskRequest
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskEvent
import ai.koog.a2a.model.TaskPushNotificationConfig
import ai.koog.a2a.server.notifications.PushNotificationConfigStorage
import ai.koog.a2a.server.notifications.PushNotificationDispatcher
import ai.koog.a2a.server.notifications.PushNotificationSender
import ai.koog.a2a.server.tasks.TaskStorage
import ai.koog.a2a.server.tasks.project
import ai.koog.a2a.transport.ServerCallContext
import ai.koog.agents.lock.KeyedMutex
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
 * - The per-task **turn slot** serializes turns: [startTurn] waits until the previous turn of the same task has ended
 *   (including its teardown), so turns of a task never overlap and start in the order their [startTurn] calls began to
 *   wait. The slot is held from the moment a turn is admitted until the turn has ended. Waiting for it holds no other
 *   lock, and a caller cancelled while waiting just leaves the queue. Only [startTurn] takes it.
 * - The **stream lock** is internal: each task stream persists an event, delivers it to every stream of the task, and
 *   updates its subscriber set under one per-task lock. This gives all streams of a task the same events in the same
 *   order, and makes [subscribe]'s snapshot and registration atomic.
 * - The per-task [TaskEventStream.processorGate] (turn gate) linearizes turn start, turn end, and [withTaskProcessor]
 *   blocks (cancellation): a block either sees the running turn (which can't end while the block runs), or sees no
 *   turn, and then no turn starts until the block is done. [startTurn] reads and checks a continued task under the
 *   gate, so a block that left the task in a terminal state makes the turn fail.
 *
 * Lock order: the turn slot, then the turn gate, then either the registry lock or the stream lock. The slot is never
 * acquired while holding another lock. [withTaskProcessor] and [subscribe] don't take it: a [startTurn] holding or
 * waiting for it may be waiting for the very turn a [withTaskProcessor] block (e.g.
 * [ai.koog.a2a.server.A2AServer.onCancelTask]) must end, and a turn can't end without the gate. The registry lock and
 * the stream lock are never nested: the registry lock is held only for short non-suspending bookkeeping and never
 * while acquiring a stream lock, so a stalled task can't block other tasks. The stream lock may be followed by the push
 * queue lock, a leaf lock held only for queue bookkeeping.
 *
 * Storage I/O happens under the gate: task reads and writes of the task stream, the task reads of [startTurn] and
 * [withTaskProcessor], and the save of a turn's inline push notification config in [startTurn]. A slow storage delays
 * only turns and [withTaskProcessor] blocks of the same task.
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
 * They run under a child [SupervisorJob] of its job, so a failing agent doesn't cancel the scope or other jobs, even if
 * the scope isn't a supervisor. Cancelling the scope cancels them all.
 * @param taskStorage The storage for tasks.
 * @param pushConfigStorage The storage for push notification configurations.
 * @param pushSender The push notification sender.
 * @param idGenerator The generator of ids of messages created by the server, e.g. the status message of a task marked
 * as failed. Defaults to [UuidIdGenerator].
 */
public class TurnManager(
    coroutineScope: CoroutineScope,
    private val taskStorage: TaskStorage,
    private val pushConfigStorage: PushNotificationConfigStorage? = null,
    pushSender: PushNotificationSender? = null,
    private val idGenerator: IdGenerator = UuidIdGenerator,
) {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    /** The scope of all jobs launched here, isolating their failures from each other and from `coroutineScope`. */
    private val scope: CoroutineScope =
        CoroutineScope(coroutineScope.coroutineContext + SupervisorJob(coroutineScope.coroutineContext[Job]))

    private val pushDispatcher: PushNotificationDispatcher? =
        if (pushConfigStorage != null && pushSender != null) {
            PushNotificationDispatcher(scope, pushConfigStorage, pushSender)
        } else {
            null
        }

    /**
     * The turn slots by task id: locked by [startTurn] and unlocked when the turn has ended (by [monitorTurn]), or when
     * it doesn't start. Fair, so waiting turns start in FIFO order.
     */
    private val turnSlots = KeyedMutex<String>()

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

    /**
     * Decides the [Turn.outcome] of one turn. Rejected and published events are recorded under the processor's lock, by
     * the agent job or a [withTaskProcessor] block, and read by the monitor after both, under the gate.
     */
    private class TurnOutcomeRecorder {
        val outcome = CompletableDeferred<TurnOutcome>()

        private var rejection: Exception? = null
        private var isEventPublished = false

        fun onRejected(e: Exception) {
            if (rejection == null) rejection = e
        }

        fun onPublished() {
            isEventPublished = true
        }

        fun complete(failure: Throwable?): TurnOutcome =
            TurnOutcome.of(failure, rejection, isEventPublished).also { outcome.complete(it) }
    }

    /** Completed by [shutdown]; ends every [subscribe] flow without taking any lock. */
    private val shutdownSignal = Job()

    /**
     * Starts a new turn of task [taskId] for [request]: runs [block] in `coroutineScope` with a [TurnEventProcessor]
     * bound to the task stream.
     *
     * First waits until the previous turn of the task, if any, has ended; turns of a task waiting here start in the
     * order they began to wait, see "Locking" above. Cancelling the caller while it waits starts nothing.
     *
     * The returned [Turn.events] is registered before [block] starts, so it receives every event of the turn.
     * If the turn continues an existing task ([request]'s [ai.koog.a2a.model.Message.taskId] is set):
     * - the task is read with [TaskStorage.get] (with [ctx] and the request's tenant) and checked, under the turn gate,
     *   so no event of the task is persisted between that read and the turn's first event;
     * - [block] receives the task as read (full history, all artifacts), and the turn keeps it as its task snapshot,
     *   with the history limited to the request's [ai.koog.a2a.model.SendMessageConfiguration.historyLength];
     * - the processor starts in task mode, see [TurnEventProcessor];
     * - the request's user message is persisted when the agent sends its first status or artifact update, see
     *   "History" above.
     *
     * The turn's context id is the continued task's, else [defaultContextId].
     *
     * [inlinePushConfig] is saved to [pushConfigStorage] once the turn is sure to start (after the checks), and before
     * [block] starts, so the turn's first task event is pushed to it. If the turn doesn't start, it isn't saved. If the
     * turn creates no task (it is for a new task and no task is stored when the turn ends, e.g. the agent replied with
     * a [ai.koog.a2a.model.Message] or never ran because `coroutineScope` was cancelled), every push notification
     * config of the task is deleted when the turn ends.
     *
     * When [block] completes: if it failed (other than by cancellation) and the task exists in a non-terminal state,
     * the task is marked [ai.koog.a2a.model.TaskState.TASK_STATE_FAILED] and every stream of the task receives that
     * update, except [Turn.events] if the failure is the rejection of one of the agent's events. An
     * [ai.koog.a2a.exceptions.A2AException] thrown before the turn published any event leaves the task unchanged
     * instead. Then the processor is closed, [Turn.events] completes, and the turn is removed.
     *
     * @param request The SendMessage request of the turn.
     * @param ctx The context of the call, used to read a continued task and to save [inlinePushConfig].
     * @param taskId The id of the turn's task: [request]'s [ai.koog.a2a.model.Message.taskId] if set, else the id
     * of the task the turn may create.
     * @param defaultContextId The context id of the turn if the task has none, e.g. a new task.
     * @param inlinePushConfig The resolved and validated push notification config of the turn's task to save, if any;
     * ignored without [pushConfigStorage].
     * @param block The agent logic of the turn. Receives the continued task, or `null` for a new task, and the
     * processor, whose [TurnEventProcessor.contextId] and [TurnEventProcessor.taskId] are those of the turn.
     * @return The started turn.
     * @throws A2ATaskNotFoundException if the continued task doesn't exist (or isn't visible to the caller).
     * @throws A2AInvalidParamsException if the message's context id differs from the continued task's.
     * @throws A2AUnsupportedOperationException if the continued task is in a terminal state.
     */
    @InternalA2AApi
    public suspend fun startTurn(
        request: SendMessageRequest,
        ctx: ServerCallContext,
        taskId: String,
        defaultContextId: String,
        inlinePushConfig: TaskPushNotificationConfig? = null,
        block: suspend (task: Task?, processor: TurnEventProcessor) -> Unit,
    ): Turn {
        val message = request.message
        require(message.taskId == null || message.taskId == taskId) {
            "taskId '$taskId' differs from the message's taskId '${message.taskId}'"
        }

        val isFollowUp = message.taskId != null
        val pushStorage = pushConfigStorage
        val savesInlinePushConfig = inlinePushConfig != null && pushStorage != null

        // Held until the turn has ended (released by monitorTurn), or released right away if the turn doesn't start.
        turnSlots.lock(taskId)
        var started = false

        try {
            // Pinned like the slot.
            val stream = acquire(taskId)
            var view: ReceiveChannel<Event>? = null

            try {
                /*
                 Under the gate no withTaskProcessor block runs, and holding the slot no other turn of the task runs, so
                 nothing publishes to the task until this turn's agent does: the task read here is the snapshot as of
                 right before the turn's first event. The turn becomes visible only after its view is registered, so a
                 block either finds the turn with its view in place or runs entirely before this read. The registry lock
                 isn't held in openTurnView, which waits for the stream lock.
                 */
                return stream.processorGate.withLock {
                    val task = if (isFollowUp) getFollowUpTask(request, ctx, taskId) else null
                    val contextId = task?.contextId ?: defaultContextId
                    val turnId = registryMutex.withLock { nextTurnId++ }

                    val channel = stream.openTurnView(turnId)
                    view = channel

                    // The view is registered: from here on, the turn starts even if the caller is cancelled.
                    withContext(NonCancellable) {
                        // If this fails, the turn doesn't start and the config may not be saved; nothing to undo.
                        if (savesInlinePushConfig) pushStorage.save(inlinePushConfig, ctx)

                        val outcomeRecorder = TurnOutcomeRecorder()
                        val processor = TurnEventProcessor(
                            contextId = contextId,
                            taskId = taskId,
                            stream = stream,
                            turnId = turnId,
                            taskExists = isFollowUp,
                            userMessage = message,
                            onRejected = outcomeRecorder::onRejected,
                            onPublished = outcomeRecorder::onPublished,
                        )
                        val agentJob = scope.async { runAgent(processor) { block(task, it) } }

                        /*
                         Ends the turn under the gate, which is held until the turn is registered below, so it can be
                         launched before that. ATOMIC: if the scope is already cancelled (server shutdown), the agent job
                         never runs, but the monitor still does and ends the turn, so the view completes, join() doesn't
                         hang, and the slot is released.
                         */
                        @OptIn(DelicateCoroutinesApi::class)
                        val monitor = scope.launch(start = CoroutineStart.ATOMIC) {
                            monitorTurn(
                                agentJob,
                                processor,
                                outcomeRecorder,
                                taskId,
                                contextId,
                                stream,
                                turnId,
                                deletesPushConfigsIfTaskAbsent = savesInlinePushConfig && !isFollowUp,
                            )
                        }
                        // The monitor releases the stream and the slot from here on.
                        started = true

                        val turn = Turn(
                            taskId = taskId,
                            contextId = contextId,
                            agentJob = agentJob,
                            eventProcessor = processor,
                            view = channel,
                            monitor = monitor,
                            taskSnapshot = task?.project(request.configuration?.historyLength, includeArtifacts = true),
                            outcome = outcomeRecorder.outcome,
                        )

                        registryMutex.withLock { turns[taskId] = turn }

                        turn
                    }
                }
            } catch (e: Throwable) {
                // Nobody collects the view: either the turn didn't start, or the caller can't get it any more.
                view?.let(stream::unsubscribe)
                throw e
            } finally {
                if (!started) release(stream)
            }
        } finally {
            if (!started) turnSlots.unlock(taskId)
        }
    }

    /**
     * Reads the task continued by [request] for [startTurn] and checks that it can take a new turn.
     *
     * @throws A2ATaskNotFoundException if the task doesn't exist (or isn't visible to the caller).
     * @throws A2AInvalidParamsException if the message's context id differs from the task's.
     * @throws A2AUnsupportedOperationException if the task is in a terminal state.
     */
    private suspend fun getFollowUpTask(request: SendMessageRequest, ctx: ServerCallContext, taskId: String): Task {
        val messageContextId = request.message.contextId
        val task = taskStorage.get(GetTaskRequest(id = taskId, tenant = request.tenant), ctx)
            ?: throw A2ATaskNotFoundException("Task '$taskId' not found")

        if (messageContextId != null && task.contextId != null && messageContextId != task.contextId) {
            throw A2AInvalidParamsException(
                "Message contextId '$messageContextId' doesn't match contextId '${task.contextId}' of task '$taskId'"
            )
        }

        if (task.status.state.terminal) {
            throw A2AUnsupportedOperationException(
                "Task '$taskId' is in terminal state ${task.status.state} and can't accept messages"
            )
        }

        return task
    }

    /**
     * Runs the agent [block]. A [CancellationException] that escapes it while the agent job is still active (e.g. from
     * the agent's own `withTimeout`) is a failure, not a cancellation: rethrown as is, it would end the turn without
     * marking the task failed.
     */
    private suspend fun runAgent(processor: TurnEventProcessor, block: suspend (TurnEventProcessor) -> Unit) {
        try {
            block(processor)
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            throw AgentCancellationFailure(e)
        }
    }

    /**
     * Waits for the turn's agent job to complete, ends the turn, and cleans up.
     *
     * @param deletesPushConfigsIfTaskAbsent whether the turn saved an inline push config for a task it was to create,
     * see [startTurn].
     */
    private suspend fun monitorTurn(
        agentJob: Deferred<Unit>,
        processor: TurnEventProcessor,
        outcomeRecorder: TurnOutcomeRecorder,
        taskId: String,
        contextId: String,
        stream: TaskEventStream,
        turnId: Long,
        deletesPushConfigsIfTaskAbsent: Boolean,
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
                     Waits for any withTaskProcessor block still publishing through this turn's processor; the agent job
                     has completed, so no supported send is in flight. Then closes the processor, decides the outcome
                     before the view can complete, closes the view (endTurn closes it even if it fails) and removes the
                     turn. Removal under the gate means a block never gets the closed processor of an ended turn.
                     */
                    stream.processorGate.withLock {
                        processor.close()

                        try {
                            stream.endTurn(turnId, contextId, outcomeRecorder.complete(failure))
                        } finally {
                            registryMutex.withLock { turns -= taskId }
                        }
                    }
                } finally {
                    try {
                        // After the turn has ended nothing can create the task anymore; Turn.join() still waits for this.
                        if (deletesPushConfigsIfTaskAbsent) deletePushConfigsIfTaskAbsent(taskId)
                        release(stream)
                    } finally {
                        // The next turn of the task may start from here on.
                        turnSlots.unlock(taskId)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Failed to finish the turn of taskId='$taskId'" }
        }
    }

    /** Deletes the push notification configs of [taskId] if the task was never stored. Failures are only logged. */
    private suspend fun deletePushConfigsIfTaskAbsent(taskId: String) {
        val pushStorage = pushConfigStorage ?: return

        try {
            if (taskStorage.get(taskId, historyLength = 0, includeArtifacts = false) == null) {
                pushStorage.deleteAll(taskId)
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete push notification configs of task '$taskId', which was not created" }
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
     * [getTask] is called first, under the turn gate, and its result is passed to [block]. No turn starts or ends
     * between that read and the end of [block] (a running agent may still change the task). An exception thrown by
     * [getTask], e.g. for an unknown task, propagates before [block] or the processor exist.
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
     * @param getTask Reads the task. Its context id is used for the new processor when no turn is running.
     * @param block The action to run.
     * @return The result of [block].
     */
    @InternalA2AApi
    public suspend fun <T> withTaskProcessor(
        taskId: String,
        getTask: suspend () -> Task,
        block: suspend (
            task: Task,
            processor: TurnEventProcessor,
            agentJob: Deferred<Unit>?,
            publish: suspend (TaskEvent) -> Unit,
        ) -> T,
    ): T {
        val stream = acquire(taskId)

        try {
            return stream.processorGate.withLock {
                val task = getTask()
                val turn = registryMutex.withLock { turns[taskId] }

                val processor = turn?.eventProcessor ?: TurnEventProcessor(
                    contextId = task.contextId.orEmpty(),
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
                    block(task, processor, turn?.agentJob, publish)
                } finally {
                    isBlockRunning = false
                    // Nothing may publish through a standalone processor once the stream may be dropped.
                    if (turn == null) processor.close()
                }
            }
        } finally {
            release(stream)
        }
    }

    /**
     * Ends every [subscribe] flow, current and future, normally (without an error), e.g. on server shutdown, including
     * those of idle tasks that no turn would ever close. Doesn't stop turns: their views end when the turns end, e.g.
     * when `coroutineScope` is cancelled. Takes no lock, so it never waits for a stalled task.
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

/**
 * The agent's own [CancellationException], rethrown from the agent job as a failure. It keeps the message of the
 * original exception, so the requester gets the same error as for any other agent crash.
 */
internal class AgentCancellationFailure(cause: CancellationException) : IllegalStateException(cause.message, cause)
