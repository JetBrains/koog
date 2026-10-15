package ai.koog.a2a.server.session

import ai.koog.a2a.annotations.InternalA2AApi
import ai.koog.a2a.exceptions.A2ATaskNotFoundException
import ai.koog.a2a.exceptions.A2AUnsupportedOperationException
import ai.koog.a2a.model.Event
import ai.koog.a2a.model.GetTaskRequest
import ai.koog.a2a.model.SendMessageRequest
import ai.koog.a2a.model.SubscribeToTaskRequest
import ai.koog.a2a.model.Task
import ai.koog.a2a.server.notifications.PushNotificationConfigStorage
import ai.koog.a2a.server.notifications.PushNotificationSender
import ai.koog.a2a.server.tasks.TaskStorage
import ai.koog.a2a.transport.ServerCallContext
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Manages the turns ([Session]s) of tasks and the per-task event streams that connect the agent to every stream of a
 * task.
 *
 * ## Turns and task streams
 *
 * - A **turn** is one [ai.koog.a2a.server.agent.AgentExecutor.execute] call, started with [startTurn] and represented
 *   by a [Session]. Its [Session.events] only cover that turn.
 * - The **task stream** is an internal, per-task broadcast through which every event of the task is persisted and
 *   delivered, in storage order, to the current turn's view and to every [subscribe] stream. It outlives turns: it
 *   exists while the task has a running turn, a [withTaskProcessor] block in progress, or at least one subscriber,
 *   and it is recreated from [TaskStorage] when needed, so idle tasks (e.g. waiting for input) hold no memory.
 *
 * ## Locking
 *
 * Two independent layers of locking are involved:
 * - **Turn serialization** is the caller's responsibility: [startTurn] fails if a turn for the same task is already
 *   running, so callers must serialize turns per task, e.g. by holding a per-task lock while waiting for
 *   [getSession]`(taskId)?.`[join][Session.join] and calling [startTurn], as [ai.koog.a2a.server.A2AServer] does.
 * - The **stream lock** is internal: each task stream persists an event, delivers it to every stream of the task, and
 *   updates its subscriber set under one per-task lock. This is what makes all streams of a task observe the same
 *   events in the same order, and makes [subscribe]'s snapshot and registration atomic.
 *
 * The per-task [TaskEventStream.processorGate] (turn gate) linearizes the start of a turn, the end of a turn, and
 * [withTaskProcessor] blocks (cancellation) of a task: [startTurn] registers the turn's view and makes the turn visible
 * under the gate, so a [withTaskProcessor] block either sees the running turn (which can't end while the block runs),
 * or sees no turn, and then no turn starts until the block is done. A turn of an existing task that would start after a
 * block left the task in a terminal state is rejected.
 *
 * Lock order: the caller's per-task task lock (see [ai.koog.a2a.server.A2AServer.tasksMutex]), then the turn gate, then
 * either the registry lock or the stream lock. The task lock is never acquired while holding the gate: a
 * [withTaskProcessor] block (e.g. [ai.koog.a2a.server.A2AServer.onCancelTask]) doesn't take it at all, since the task
 * lock can be held by a caller waiting for a running turn to end ([Session.join]), and a turn can't end without the
 * gate. The registry lock and the stream lock are never nested: the registry lock is held only for short,
 * non-suspending bookkeeping and is **never** held while acquiring a stream lock (or anything else that can wait for a
 * fan-out), so a stalled task can't block the other tasks.
 *
 * ## Back-pressure
 *
 * Delivery is synchronous: the agent's `sendTaskEvent`/`sendMessage` returns only after every stream of the task, i.e.
 * the requester's [Session.events], the collector of a blocking SendMessage, and every [subscribe] flow, has accepted
 * the event. Nothing is buffered or dropped, and no collector is disconnected for being slow. A slow collector
 * therefore slows the agent and the other collectors of the task, and operations that need the task's stream lock
 * ([subscribe], [startTurn], turn end, [withTaskProcessor] publishing) wait behind it. Only operations on that very
 * task are affected: other tasks, [getSession], and [activeSessions] never wait for it.
 *
 * A collector that is cancelled (e.g. the client went away) stops being a delivery target immediately: the delivery
 * that is waiting for it fails, the collector is dropped, and the agent and the other collectors go on.
 *
 * ## History
 *
 * The user message of a turn is persisted into [ai.koog.a2a.model.Task.history] by the task stream, deduplicated by
 * [ai.koog.a2a.model.Message.messageId]:
 * - for a follow-up, lazily, right before the first status or artifact update the agent sends in the turn: the task's
 *   current status message is moved into the history, the user message is appended, and the status message is cleared.
 *   If the agent sends no such event, the user message is not persisted;
 * - for a new task, when the agent sends the initial [ai.koog.a2a.model.Task].
 *
 * In both cases the stored copy of the user message has its task id and context id set to those of the task.
 *
 * An agent's [ai.koog.a2a.model.Task] event for an existing task is ignored.
 *
 * ## Push notifications
 *
 * If [pushConfigStorage] and [pushSender] are configured, after each turn that sent task events, a push notification
 * with the current state of the task is sent to every push notification config of the task.
 *
 * @param coroutineScope The scope in which agent jobs and turn monitoring jobs are launched.
 * @param taskStorage The storage for tasks.
 * @param pushConfigStorage The storage for push notification configurations.
 * @param pushSender The push notification sender.
 * @param idGenerator The generator of ids of messages created by the server, e.g. the status message of a task marked
 * as failed. Defaults to [UuidIdGenerator].
 */
public class SessionManager(
    private val coroutineScope: CoroutineScope,
    private val taskStorage: TaskStorage,
    private val pushConfigStorage: PushNotificationConfigStorage? = null,
    private val pushSender: PushNotificationSender? = null,
    private val idGenerator: IdGenerator = UuidIdGenerator,
) {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    private class StreamEntry(val stream: TaskEventStream) {
        /**
         * Number of turns, [withTaskProcessor] blocks, and subscribers using the stream.
         */
        var holders: Int = 0
    }

    /**
     * Guards [streams], [turns], and [nextTurnId]. May be acquired while holding [TaskEventStream.processorGate]. Since
     * a stream lock can be held for a long time by a fan-out waiting for a slow collector, this lock is never held
     * while acquiring a stream lock or anything else that may wait for a fan-out, and only short non-suspending
     * bookkeeping runs under it.
     */
    private val registryMutex = Mutex()
    private val streams = mutableMapOf<String, StreamEntry>()

    /**
     * Map of task id to the session of the running turn. All sessions have a task id, even if the task won't be
     * created. A turn is added once its view is registered, and removed once it has ended, both under the task's
     * [TaskEventStream.processorGate].
     */
    private val turns = mutableMapOf<String, Session>()
    private var nextTurnId = 0L

    /**
     * Starts a new turn for [RequestContext.taskId]: runs [block] in [coroutineScope] with a [SessionEventProcessor]
     * bound to the task stream.
     *
     * The returned session's [Session.events] is registered before [block] starts, so it receives every event of the
     * turn. If the turn continues an existing task ([RequestContext.task] is not `null`):
     * - the processor starts in task mode, see [SessionEventProcessor];
     * - the user message of the request ([ai.koog.a2a.model.SendMessageRequest.message]) is persisted into the history
     *   of the stored task when the agent sends its first status or artifact update, see "History" above;
     * - the stored task is read atomically with the registration of [Session.events] (with the history limited to
     *   the request's [ai.koog.a2a.model.SendMessageConfiguration.historyLength] and all artifacts) and kept as the
     *   session's task snapshot;
     * - if the stored task is in a terminal state by then (e.g. it was cancelled after the caller validated it), the
     *   turn isn't started.
     *
     * If the turn creates a new task, the user message is added to the history of the agent's initial
     * [ai.koog.a2a.model.Task] instead.
     *
     * When [block] completes:
     * - if it failed with an exception (other than a cancellation) and the task exists in a non-terminal state, the
     *   task is marked [ai.koog.a2a.model.TaskState.TASK_STATE_FAILED] and every stream of the task receives that
     *   update;
     * - the processor is closed, [Session.events] completes, and the session is removed.
     *
     * Turns of the same task must be serialized by the caller, see [SessionManager].
     *
     * @param context The request context of the turn; its [RequestContext.contextId] and [RequestContext.taskId] are
     * enforced by the processor.
     * @param block The agent logic of the turn.
     * @return The session of the started turn.
     * @throws IllegalStateException if a turn for the same task is already running.
     * @throws A2AUnsupportedOperationException if the turn continues an existing task that is in a terminal state.
     */
    @InternalA2AApi
    public suspend fun startTurn(
        context: RequestContext<SendMessageRequest>,
        block: suspend (SessionEventProcessor) -> Unit,
    ): Session {
        val taskId = context.taskId
        val contextId = context.contextId
        val taskExists = context.task != null

        // Pinned until the turn has ended (released by monitorTurn), or right away if the turn doesn't start.
        val stream = acquire(taskId)
        var view: ReceiveChannel<Event>? = null
        var started = false

        try {
            /*
             Under the gate, no withTaskProcessor block (e.g. a cancellation) runs for the task, and the turn becomes
             visible only after its view is registered, so a block either finds the turn with its view in place or
             runs entirely before the turn starts. In the latter case a terminal state it forced is in the snapshot.
             The registry lock isn't held while waiting for the stream lock in openTurnView: the stream lock can be held
             for long by a fan-out waiting for a slow collector, and the registry lock must not wait for it.
             */
            return stream.processorGate.withLock {
                val turnId = registryMutex.withLock {
                    check(taskId !in turns) {
                        "Session for taskId '$taskId' already runs."
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
                    val processor = SessionEventProcessor(
                        contextId = contextId,
                        taskId = taskId,
                        stream = stream,
                        turnId = turnId,
                        taskExists = taskExists,
                        userMessage = context.params.message,
                    )

                    val session = Session(
                        taskId = taskId,
                        contextId = contextId,
                        agentJob = coroutineScope.async(start = CoroutineStart.LAZY) { block(processor) },
                        eventProcessor = processor,
                        events = channel.consumeAsFlow(),
                        completion = Job(),
                        taskSnapshot = snapshot,
                    )

                    registryMutex.withLock { turns[taskId] = session }
                    started = true

                    // Starts the agent job; ends the turn under the gate, i.e. after this registration is done.
                    coroutineScope.launch {
                        monitorTurn(session, stream, turnId)
                    }

                    session
                }
            }
        } catch (e: Throwable) {
            // The turn didn't start, or it did but the caller is gone: either way, nobody collects the view.
            view?.let(stream::unsubscribe)
            throw e
        } finally {
            if (!started) release(stream)
        }
    }

    /**
     * Waits for the turn's agent job to complete (starting it, if needed), ends the turn, and cleans up.
     */
    private suspend fun monitorTurn(session: Session, stream: TaskEventStream, turnId: Long) {
        // Also fails with a CancellationException if this monitor is cancelled, e.g. on server shutdown.
        val failure = try {
            session.agentJob.await()
            null
        } catch (e: Throwable) {
            e
        }

        if (failure != null && failure !is CancellationException) {
            // The requester may have got its response already, so the failure must not go unnoticed.
            logger.warn(failure) { "Agent execution failed for taskId='${session.taskId}'" }
        }

        try {
            // The turn is ended even if this monitor is cancelled, so its view completes and the session is removed.
            withContext(NonCancellable) {
                try {
                    /*
                     Wait for any withTaskProcessor block (e.g. cancellation) that may still publish through this
                     turn's processor, then close the processor and the turn view (endTurn closes it even if it fails),
                     and remove the session. The session is removed under the gate, so a withTaskProcessor block never
                     gets the closed processor of an ended turn.
                     */
                    stream.processorGate.withLock {
                        session.eventProcessor.close()

                        try {
                            stream.endTurn(turnId, session.contextId, failure)
                        } finally {
                            registryMutex.withLock { turns -= session.taskId }
                        }
                    }
                } finally {
                    release(stream)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Failed to finish the turn of taskId='${session.taskId}'" }
        } finally {
            session.completion.complete()
        }

        if (session.eventProcessor.isTaskEventSent) {
            sendPushNotifications(session.taskId)
        }
    }

    /**
     * Sends push notifications with the current state of the task, if configured.
     */
    private suspend fun sendPushNotifications(taskId: String) {
        if (pushSender == null || pushConfigStorage == null) return

        val task = taskStorage.get(taskId, historyLength = 0, includeArtifacts = false) ?: return

        pushConfigStorage.getAll(taskId).forEach { config ->
            try {
                pushSender.send(config, task)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn(e) { "Failed to send push notification for taskId='$taskId' configId='${config.id}'" }
            }
        }
    }

    /**
     * Subscribes to the events of a task (SubscribeToTask).
     *
     * The first event is the task as returned by [TaskStorage.get] for a [GetTaskRequest] built from [request]
     * (full history and artifacts), followed by every later event of the task, across turns, including turns started
     * later by other requests. The snapshot is read and the subscription is registered atomically, so no event is
     * missed or repeated. The flow completes when the task reaches a terminal state.
     *
     * Errors are thrown on collection, before the first event is emitted.
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

                for (event in channel) {
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
     * Runs [block] with a [SessionEventProcessor] bound to the task stream of [taskId], e.g. to cancel a task.
     *
     * If a turn of the task is running, [block] receives that turn's processor and agent job, and the turn is kept
     * from finishing (its processor and [Session.events] stay open) until [block] returns, so everything [block]
     * publishes reaches the turn's requester as well. Otherwise, [block] receives a new processor, which is closed when
     * [block] returns (later events are rejected with [ai.koog.a2a.server.exceptions.SessionNotActiveException]), and
     * `null`. Either way, the events [block] publishes reach every [subscribe] stream of the task.
     *
     * No turn of the task starts while [block] runs: a turn that is being started waits, and is rejected if [block]
     * left the task in a terminal state, see [startTurn].
     *
     * [block] may cancel and join the agent job, but must not wait for the turn to end (e.g. with [Session.join]) or
     * for a turn to start, since neither can happen while [block] runs.
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
        block: suspend (processor: SessionEventProcessor, agentJob: Deferred<Unit>?) -> T,
    ): T {
        val stream = acquire(taskId)

        try {
            return stream.processorGate.withLock {
                val session = registryMutex.withLock { turns[taskId] }

                val processor = session?.eventProcessor ?: SessionEventProcessor(
                    contextId = contextId,
                    taskId = taskId,
                    stream = stream,
                    turnId = null,
                )

                try {
                    block(processor, session?.agentJob)
                } finally {
                    /*
                     Nothing may publish through a standalone processor once the block is done and the stream may be
                     dropped. Closed without waiting for a send in progress, which could hold the gate for long.
                     */
                    if (session == null) processor.closeImmediately()
                }
            }
        } finally {
            release(stream)
        }
    }

    /**
     * Returns the session of the running turn of the given task id, if any. A turn that is still being started by
     * [startTurn] isn't reported until its [Session.events] is registered.
     */
    public suspend fun getSession(taskId: String): Session? = registryMutex.withLock {
        turns[taskId]
    }

    /**
     * Returns the number of running turns.
     */
    public suspend fun activeSessions(): Int = registryMutex.withLock {
        turns.size
    }

    /**
     * Returns the number of task streams currently held in memory.
     */
    internal suspend fun activeStreams(): Int = registryMutex.withLock {
        streams.size
    }

    private suspend fun acquire(taskId: String): TaskEventStream = registryMutex.withLock {
        val entry = streams.getOrPut(taskId) {
            StreamEntry(TaskEventStream(taskId, taskStorage, idGenerator))
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
