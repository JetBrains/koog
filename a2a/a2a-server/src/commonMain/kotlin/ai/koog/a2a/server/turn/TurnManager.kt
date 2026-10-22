package ai.koog.a2a.server.turn

import ai.koog.a2a.annotations.InternalA2AApi
import ai.koog.a2a.exceptions.A2AInternalErrorException
import ai.koog.a2a.exceptions.A2AInvalidParamsException
import ai.koog.a2a.exceptions.A2ATaskNotFoundException
import ai.koog.a2a.exceptions.A2AUnsupportedOperationException
import ai.koog.a2a.model.Event
import ai.koog.a2a.model.GetTaskRequest
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.SendMessageRequest
import ai.koog.a2a.model.SubscribeToTaskRequest
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskEvent
import ai.koog.a2a.model.TaskPushNotificationConfig
import ai.koog.a2a.server.notifications.PushNotificationConfigStorage
import ai.koog.a2a.server.notifications.PushNotificationDispatcher
import ai.koog.a2a.server.notifications.PushNotificationSender
import ai.koog.a2a.server.tasks.TaskStorage
import ai.koog.a2a.server.tasks.isTerminal
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
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.Volatile
import kotlin.time.Duration

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
 * The processor's lock, which serializes its sends, sits between the gate and the stream lock: a [withTaskProcessor]
 * block sending through a processor takes the gate, then the processor's lock, then the stream lock, and the agent
 * takes the processor's lock, then the stream lock, without the gate. Nothing takes the gate while holding the
 * processor's lock. The internal lock of the slots' [KeyedMutex] is a leaf lock shared by all tasks.
 *
 * Storage I/O happens under the gate: task reads and writes of the task stream, the task reads of [startTurn] and
 * [withTaskProcessor], and the save of a turn's inline push notification config in [startTurn]. A slow storage delays
 * only turns and [withTaskProcessor] blocks of the same task.
 *
 * ## Shutdown
 *
 * [shutdownGracefully] switches the server to [LifecycleState.DRAINING] under the registry lock. That switch is
 * decisive: [startTurn] checks the state under that lock right before the turn's side effects (saving the inline push
 * notification config, launching the agent) and counts the turn as admitted there, so no turn is admitted after it, and
 * the drain waits until the admitted count drops to zero, which then is final. The count includes turns not yet in
 * [getTurn] (still being started), and a turn leaves it only when its monitor has fully ended. Then the drain waits for
 * the push queue to be empty, and ends with [shutdown], as does the deadline. [subscribe] streams aren't waited for:
 * they stay open during the drain and are ended by [shutdown].
 *
 * [shutdown] switches the server to [LifecycleState.SHUT_DOWN], cancels every turn view and [subscribe] channel with
 * [ServerShutdownException], and then cancels every job launched here. It doesn't take any lock: a [startTurn] that
 * passed its check just before may still start, in the cancelled scope, so its agent never runs, and its view is
 * cancelled too.
 *
 * The channels are cancelled first, without the stream lock, so no delivery waits for a stalled collector any more,
 * not even the non-cancellable delivery of a failed turn's FAILED update. Each channel is registered with
 * [shutdownSignal] (a lock-free handler list) when it is opened, and unregistered when it is done. Shutdown is thus
 * bounded, except for non-cancellable storage I/O (persisting an event, saving an inline push notification config) and
 * the [ai.koog.a2a.server.agent.AgentExecutor.cancel] of a running CancelTask, which holds the task's gate: a turn of
 * that task can't end until they return.
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
 * receives the events of its task in storage order. Failed sends are logged and not retried. [shutdownGracefully] waits
 * for the pending notifications; they are dropped on [shutdown] or when [coroutineScope] is cancelled.
 *
 * @param coroutineScope The scope in which agent jobs, turn monitoring jobs, and push notification jobs are launched.
 * They run under a child [SupervisorJob] of its job, so a failing agent doesn't cancel the scope or other jobs, even if
 * the scope isn't a supervisor. [shutdown] cancels only that child. Cancelling the scope cancels them all and counts as
 * a shutdown, see [lifecycleState].
 * @param taskStorage The storage for tasks.
 * @param pushConfigStorage The storage for push notification configurations.
 * @param pushSender The push notification sender.
 * @param idGenerator The generator of the context id of a turn that has none, and of ids of messages created by the
 * server, e.g. the generic status message of a task marked as failed. Defaults to [UuidIdGenerator].
 * @param failureMessage Builds the status message of a task marked as failed when its turn fails, see
 * [ai.koog.a2a.server.A2AServer.failureMessage]. Called by the turn's monitor with no lock held and cancellably, before
 * the turn ends, with the task id, the turn's context id, and the agent's exception. If it throws or is cancelled, the
 * generic message is used. Defaults to the generic message.
 */
@InternalA2AApi
public class TurnManager(
    coroutineScope: CoroutineScope,
    private val taskStorage: TaskStorage,
    private val pushConfigStorage: PushNotificationConfigStorage? = null,
    pushSender: PushNotificationSender? = null,
    private val idGenerator: IdGenerator = UuidIdGenerator,
    private val failureMessage: suspend (taskId: String, contextId: String, error: Throwable) -> Message =
        { taskId, contextId, _ -> idGenerator.genericFailureMessage(taskId, contextId) },
) {
    /*
     Design rule: methods take data, not caller callbacks that run under this class's locks (the slot, the gate, the
     stream lock). Such callbacks carry hidden locking rules (e.g. calling back into the turn layer deadlocks) and make
     the error and cleanup order part of the contract; callbacks run outside our locks are fine. The only lambdas are the
     agent block, which runs in the agent job under no lock, the withTaskProcessor block, the cancel logic, which by
     design runs under the gate, and failureMessage, which runs under no lock and whose result is passed on as data.
     */

    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    /** The parent of all jobs launched here, isolating their failures from each other and from `coroutineScope`. */
    private val supervisor = SupervisorJob(coroutineScope.coroutineContext[Job])

    private val scope: CoroutineScope = CoroutineScope(coroutineScope.coroutineContext + supervisor)

    /**
     * Cancelled by [shutdown], or with [supervisor] when `coroutineScope` is cancelled; its cancellation is the switch to
     * [LifecycleState.SHUT_DOWN], see [lifecycleState]. Its completion handlers cancel
     * the open turn views and [subscribe] channels, see [cancelOnShutdown]. The first child of [supervisor], so even a
     * cancellation from outside reaches it before any agent job.
     */
    private val shutdownSignal = Job(supervisor)

    /** Set by [shutdownGracefully] under the registry lock, read without a lock, see "Shutdown" above. */
    @Volatile
    private var isDraining = false

    /**
     * The number of turns admitted by [startTurn] whose monitor hasn't ended yet. Incremented under the registry lock
     * by the check of [startTurn], see "Shutdown" above.
     */
    private val admittedTurns = MutableStateFlow(0)

    /**
     * The current lifecycle state. [LifecycleState.SHUT_DOWN] once [shutdownSignal] is cancelled: by [shutdown], or
     * once `coroutineScope` is cancelled from outside, since no job can run any more; the streams are then ended as on
     * [shutdown].
     */
    internal val lifecycleState: LifecycleState
        get() = when {
            !shutdownSignal.isActive -> LifecycleState.SHUT_DOWN
            isDraining -> LifecycleState.DRAINING
            else -> LifecycleState.RUNNING
        }

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
     * Guards [streams], [turns], [isDraining], and the lifecycle check of [startTurn], see "Shutdown" above. May be
     * acquired while holding [TaskEventStream.processorGate], but is never held while acquiring a stream lock, which a
     * fan-out may hold for long.
     */
    private val registryMutex = Mutex()
    private val streams = mutableMapOf<String, StreamEntry>()

    /**
     * Running turns by task id. Added once the turn's view is registered and removed once it has ended, both under the
     * task's [TaskEventStream.processorGate].
     */
    private val turns = mutableMapOf<String, Turn>()

    /**
     * Decides the [Turn.outcome] of one turn. Rejected and published events are recorded under the processor's lock, by
     * the agent job or a [withTaskProcessor] block, and read by the monitor after both, under the gate.
     */
    private class TurnOutcomeRecorder {
        val outcome = CompletableDeferred<TurnOutcome>()

        private var rejection: Exception? = null

        /** Read by [endsFailed] without the processor's lock. */
        @Volatile
        private var isEventPublished = false

        fun onRejected(e: Exception) {
            if (rejection == null) rejection = e
        }

        fun onPublished() {
            isEventPublished = true
        }

        fun complete(failure: Throwable?): TurnOutcome =
            TurnOutcome.of(failure, rejection, isEventPublished).also { outcome.complete(it) }

        /**
         * Whether [complete] will return [TurnOutcome.Failed] or [TurnOutcome.Rejected] for [failure], checked before
         * the gate. A `false` may be outdated: a withTaskProcessor block still publishing through the processor turns a
         * [TurnOutcome.RequestError] into [TurnOutcome.Failed]. A `true` is final.
         */
        fun endsFailed(failure: Throwable): Boolean =
            when (TurnOutcome.of(failure, rejection = null, isEventPublished)) {
                is TurnOutcome.Failed, is TurnOutcome.Rejected -> true
                is TurnOutcome.Completed, is TurnOutcome.RequestError -> false
            }
    }

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
     * The turn's context id is the continued task's, else the message's, else one generated with
     * [IdGenerator.generateContextId] (called only then).
     *
     * The turn is rejected, starting nothing, if the server isn't [LifecycleState.RUNNING], checked under the registry
     * lock after the slot and the gate are acquired, so it also rejects a turn that waited for the previous one. Once
     * past that check, the turn is admitted: [shutdownGracefully] waits for it.
     *
     * [inlinePushConfig] is saved to [pushConfigStorage] once the turn is sure to start (after the checks), and before
     * [block] starts, so the turn's first task event is pushed to it. If the turn doesn't start, it isn't saved. If the
     * turn creates no task (it is for a new task and no task is stored when the turn ends, e.g. the agent replied with
     * a [ai.koog.a2a.model.Message] or never ran because the server was shut down meanwhile), every push notification
     * config of the task is deleted when the turn ends.
     *
     * When [block] completes: if it failed (other than by cancellation) and the task exists in a non-terminal state,
     * the task is marked [ai.koog.a2a.model.TaskState.TASK_STATE_FAILED], with the status message from
     * `failureMessage`, and every stream of the task receives that update, except [Turn.events] if the failure is the
     * rejection of one of the agent's events. An
     * [ai.koog.a2a.exceptions.A2AException] thrown before the turn published any event leaves the task unchanged
     * instead. Then the processor is closed, [Turn.events] completes, and the turn is removed.
     *
     * @param request The SendMessage request of the turn.
     * @param ctx The context of the call, used to read a continued task and to save [inlinePushConfig].
     * @param taskId The id of the turn's task: [request]'s [ai.koog.a2a.model.Message.taskId] if set, else the id
     * of the task the turn may create.
     * @param inlinePushConfig The resolved and validated push notification config of the turn's task to save, if any;
     * ignored without [pushConfigStorage].
     * @param block The agent logic of the turn. Receives the continued task, or `null` for a new task, and the
     * processor, whose [TurnEventProcessor.contextId] and [TurnEventProcessor.taskId] are those of the turn.
     * @return The started turn.
     * @throws A2ATaskNotFoundException if the continued task doesn't exist (or isn't visible to the caller).
     * @throws A2AInvalidParamsException if the message's context id differs from the continued task's.
     * @throws A2AUnsupportedOperationException if the continued task is in a terminal state.
     * @throws A2AInternalErrorException if the server is shutting down or shut down, see [LifecycleState.checkAccepting].
     */
    public suspend fun startTurn(
        request: SendMessageRequest,
        ctx: ServerCallContext,
        taskId: String,
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
        var admitted = false
        var started = false

        try {
            // Pinned like the slot.
            val stream = acquire(taskId)
            var view: ReceiveChannel<Event>? = null
            var shutdownHandle: DisposableHandle? = null

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
                    val contextId = task?.contextId ?: message.contextId ?: idGenerator.generateContextId(message)

                    val channel = stream.openTurnView()
                    view = channel
                    val viewShutdownHandle = cancelOnShutdown(channel)
                    shutdownHandle = viewShutdownHandle

                    // The decisive check, right before the turn's side effects, see "Shutdown" above.
                    registryMutex.withLock {
                        lifecycleState.checkAccepting()
                        admittedTurns.update { it + 1 }
                        admitted = true
                    }

                    // The view is registered: from here on, the turn starts even if the caller is cancelled.
                    withContext(NonCancellable) {
                        // If this fails, the turn doesn't start and the config may not be saved; nothing to undo.
                        if (savesInlinePushConfig) pushStorage.save(inlinePushConfig, ctx)

                        val outcomeRecorder = TurnOutcomeRecorder()
                        val processor = TurnEventProcessor(
                            contextId = contextId,
                            taskId = taskId,
                            stream = stream,
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
                            try {
                                monitorTurn(
                                    agentJob,
                                    processor,
                                    outcomeRecorder,
                                    taskId,
                                    contextId,
                                    stream,
                                    viewShutdownHandle,
                                    deletesPushConfigsIfTaskAbsent = savesInlinePushConfig && !isFollowUp,
                                )
                            } finally {
                                // The turn has fully ended, as for Turn.join().
                                admittedTurns.update { it - 1 }
                            }
                        }
                        // The monitor releases the stream and the slot, and ends the admission, from here on.
                        started = true

                        val turn = Turn(
                            taskId = taskId,
                            contextId = contextId,
                            agentJob = agentJob,
                            eventProcessor = processor,
                            view = channel,
                            viewShutdownHandle = viewShutdownHandle,
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
                shutdownHandle?.dispose()
                view?.let(stream::unsubscribe)
                throw e
            } finally {
                if (!started) release(stream)
            }
        } finally {
            if (!started) {
                turnSlots.unlock(taskId)
                if (admitted) admittedTurns.update { it - 1 }
            }
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
     * @param viewShutdownHandle the registration of the turn's view with [shutdownSignal], disposed once the view is
     * closed.
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
        viewShutdownHandle: DisposableHandle,
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

        // Before the gate, so the caller's code runs under no lock. Null: the generic message.
        val failureMessage = failure
            ?.takeIf(outcomeRecorder::endsFailed)
            ?.let { buildFailureMessage(taskId, contextId, it) }

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
                            // A FAILED update waits here for a stalled collector until it goes away or the server shuts
                            // down, see "Shutdown" above.
                            stream.endTurn(contextId, outcomeRecorder.complete(failure), failureMessage)
                        } finally {
                            // The view is closed: a shutdown can't change how it ends any more.
                            viewShutdownHandle.dispose()
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

    /**
     * Calls [failureMessage] with the agent's own exception, cancellably. Returns `null` if it fails or is cancelled
     * (e.g. on shutdown): the turn must end regardless.
     */
    private suspend fun buildFailureMessage(taskId: String, contextId: String, failure: Throwable): Message? = try {
        failureMessage(taskId, contextId, (failure as? AgentCancellationFailure)?.cause ?: failure)
    } catch (e: Exception) {
        if (e !is CancellationException || currentCoroutineContext().isActive) {
            logger.error(e) { "Failed to build the failure message of taskId='$taskId', using the generic one" }
        }
        null
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
     * event is missed or repeated. The flow completes when the task reaches a terminal state. On [shutdown] (also once
     * `coroutineScope` is cancelled), it fails with [ServerShutdownException] instead, unless it has emitted the
     * terminal event already.
     *
     * Other errors are thrown on collection, before the first event.
     *
     * @param request The SubscribeToTask request.
     * @param ctx The context of the call, passed to [TaskStorage.get] to scope the lookup.
     * @return A cold flow; each collection is a separate subscription.
     * @throws A2ATaskNotFoundException if the task doesn't exist (or isn't visible to the caller).
     * @throws A2AUnsupportedOperationException if the task is already in a terminal state.
     */
    public fun subscribe(request: SubscribeToTaskRequest, ctx: ServerCallContext): Flow<Event> = flow {
        val stream = acquire(request.id)

        try {
            val (snapshot, channel) = stream.subscribe {
                taskStorage.get(GetTaskRequest(id = request.id, tenant = request.tenant), ctx)
            }
            val shutdownHandle = cancelOnShutdown(channel)

            try {
                emit(snapshot)

                // Ends when the channel is closed after a terminal event; fails if it is cancelled on shutdown.
                for (event in channel) {
                    emit(event)

                    // The subscription is complete: a shutdown during the emit above must not fail it.
                    if (event.isTerminal()) break
                }
            } finally {
                shutdownHandle.dispose()
                // Cancels the channel without the stream lock, so a delivery waiting for this collector fails.
                stream.unsubscribe(channel)
            }
        } finally {
            release(stream)
        }
    }

    /**
     * Runs [block] with a [TurnEventProcessor] bound to the task stream of the task [lookup] identifies, e.g. to cancel
     * a task.
     *
     * The task is first read with [TaskStorage.get] ([lookup], [ctx]) under the turn gate and passed to [block]. No turn
     * starts or ends between that read and the end of [block] (a running agent may still change the task).
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
     * @param lookup The task to read, with the history and artifacts [block] needs. The task's context id is used for
     * the new processor when no turn is running.
     * @param ctx The context of the call, passed to [TaskStorage.get] to scope the lookup.
     * @param block The action to run.
     * @return The result of [block].
     * @throws A2ATaskNotFoundException if the task doesn't exist (or isn't visible to the caller), before [block] or
     * the processor exist.
     */
    public suspend fun <T> withTaskProcessor(
        lookup: GetTaskRequest,
        ctx: ServerCallContext,
        block: suspend (
            task: Task,
            processor: TurnEventProcessor,
            agentJob: Deferred<Unit>?,
            publish: suspend (TaskEvent) -> Unit,
        ) -> T,
    ): T {
        val taskId = lookup.id
        val stream = acquire(taskId)

        try {
            return stream.processorGate.withLock {
                val task = taskStorage.get(lookup, ctx) ?: throw A2ATaskNotFoundException("Task '$taskId' not found")
                val turn = registryMutex.withLock { turns[taskId] }

                val processor = turn?.eventProcessor ?: TurnEventProcessor(
                    contextId = task.contextId.orEmpty(),
                    taskId = taskId,
                    stream = stream,
                    // The task exists: a Message is rejected like in any turn of an existing task.
                    taskExists = true,
                )

                var isBlockRunning = true
                val publish: suspend (TaskEvent) -> Unit = { event ->
                    check(isBlockRunning) { "Can't publish after the withTaskProcessor block has returned" }
                    require(event.taskId == taskId) { "Event of task '${event.taskId}' can't be published to task '$taskId'" }
                    stream.publish(event)
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
     * Shuts down gracefully, waiting at most [gracePeriod]:
     * 1. switches to [LifecycleState.DRAINING] (unless already shut down), so later turns are rejected (see
     *    [startTurn]); [subscribe] streams stay open;
     * 2. waits until every turn admitted before has fully ended (as [Turn.join]), then until no push notification is
     *    pending;
     * 3. then, or at the deadline, whichever comes first, shuts down as [shutdown] does, which also ends the
     *    [subscribe] streams.
     *
     * Returns once the server is [LifecycleState.SHUT_DOWN], also when that happens otherwise meanwhile: by [shutdown],
     * by another call of this function whose deadline comes earlier or whose drain completes, or when `coroutineScope`
     * is cancelled. So concurrent calls return together, at the earliest deadline at the latest. If the caller is
     * cancelled while waiting, shuts down at once, then rethrows the [CancellationException]. A non-positive
     * [gracePeriod] shuts down at once.
     *
     * The wait for the push notifications also covers those queued meanwhile, e.g. by a CancelTask; one queued after
     * the drain completed is dropped by the shutdown.
     *
     * @param gracePeriod How long to wait for the running turns and pending push notifications.
     */
    public suspend fun shutdownGracefully(gracePeriod: Duration) {
        try {
            registryMutex.withLock { isDraining = true }

            withTimeoutOrNull(gracePeriod) {
                coroutineScope {
                    val drain = async { awaitDrained() }
                    select {
                        drain.onAwait {}
                        // Shut down meanwhile: the admitted turns may never be counted out, e.g. after the scope's
                        // cancellation they can still be waiting for storage I/O.
                        shutdownSignal.onJoin {}
                    }
                    drain.cancel()
                }
            }
        } finally {
            shutdown()
        }
    }

    /** Waits for the admitted turns, then for the push notifications, see [shutdownGracefully]. */
    private suspend fun awaitDrained() {
        // Final once zero: no turn is admitted while draining.
        admittedTurns.first { it == 0 }
        pushDispatcher?.awaitIdle()
    }

    /**
     * Shuts down immediately, in this order:
     * 1. switches to [LifecycleState.SHUT_DOWN], so later turns are rejected (see [startTurn]);
     * 2. cancels every open [Turn.events] and [subscribe] channel, and every one opened later, with
     *    [ServerShutdownException], so their flows fail with it (a channel already closed, e.g. at a terminal state,
     *    still ends normally), and no delivery waits for their collectors any more;
     * 3. cancels every agent job, turn monitor, and push notification job, dropping the pending notifications.
     *
     * `coroutineScope` itself isn't cancelled. Running turns end once their cancelled agents stop; their tasks keep
     * their last persisted state. Cancelling `coroutineScope` from outside does all three steps as well. A running
     * [shutdownGracefully] returns.
     *
     * Takes no lock and doesn't wait, so it never waits for a stalled task; see "Shutdown" above for what the jobs may
     * still wait for. Idempotent.
     *
     * @param cause The cause of the cancellation of the jobs.
     */
    public fun shutdown(cause: CancellationException? = null) {
        // Switches to SHUT_DOWN, before the jobs are cancelled, which may be delivering to the channels.
        shutdownSignal.cancel()
        supervisor.cancel(cause)
    }

    /**
     * Cancels [channel] with [ServerShutdownException] once [shutdownSignal] is cancelled, right away if it already is.
     * Dispose the returned handle once the channel is closed or cancelled otherwise. Lock-free: the cancellation runs on
     * the thread that shuts down.
     */
    private fun cancelOnShutdown(channel: ReceiveChannel<Event>): DisposableHandle =
        shutdownSignal.invokeOnCompletion { channel.cancel(ServerShutdownException()) }

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
 * Cancels the turn views and [TurnManager.subscribe] channels on [TurnManager.shutdown], so their consumers can tell a
 * shutdown from their own cancellation (e.g. the client went away).
 */
internal class ServerShutdownException : CancellationException(SHUT_DOWN_MESSAGE) {
    /** The error a stream ended by the shutdown fails with, the same as for requests rejected after it. */
    fun toA2AException(): A2AInternalErrorException = A2AInternalErrorException(SHUT_DOWN_MESSAGE)
}

private const val SHUT_DOWN_MESSAGE = "Server is shut down"

/**
 * The agent's own [CancellationException], rethrown from the agent job as a failure. It keeps the message of the
 * original exception, so the requester gets the same error as for any other agent crash.
 */
internal class AgentCancellationFailure(cause: CancellationException) : IllegalStateException(cause.message, cause)

/**
 * The lifecycle states of the server, see [TurnManager.lifecycleState].
 */
internal enum class LifecycleState {
    /** Accepting every request. */
    RUNNING,

    /** Shutting down gracefully: no new turns or subscriptions, the running turns finish. */
    DRAINING,

    /** Shut down: no request is accepted. */
    SHUT_DOWN;

    /**
     * Throws the shutdown error unless [RUNNING].
     *
     * @throws A2AInternalErrorException "Server is shutting down" if [DRAINING], "Server is shut down" if [SHUT_DOWN].
     */
    fun checkAccepting() {
        when (this) {
            RUNNING -> Unit
            DRAINING -> throw A2AInternalErrorException("Server is shutting down")
            SHUT_DOWN -> throw A2AInternalErrorException(SHUT_DOWN_MESSAGE)
        }
    }
}
