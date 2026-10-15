package ai.koog.a2a.server.session

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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.emitAll
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
 * Lock order: [TaskEventStream.processorGate] (turn-end gate), then the registry lock; and the gate, then the stream
 * lock. The registry lock and the stream lock are never nested: the registry lock is held only for short, non-suspending
 * bookkeeping and is **never** held while acquiring a stream lock (or anything else that can wait for a fan-out), so a
 * stalled task can't block the other tasks.
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
     * A running turn.
     */
    private class Turn(val session: Session) {
        /**
         * Completes once the turn's view is registered in the task stream, i.e. the turn is ready to run and publish
         * (`true`), or once the registration failed and the turn has been removed (`false`).
         */
        val registered = CompletableDeferred<Boolean>()
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
     * Map of task id to the running turn. All sessions have a task id, even if the task won't be created.
     */
    private val turns = mutableMapOf<String, Turn>()
    private var nextTurnId = 0L

    /**
     * Starts a new turn for [RequestContext.taskId]: runs [block] in [coroutineScope] with a [SessionEventProcessor]
     * bound to the task stream.
     *
     * The returned session's [Session.events] is registered before [block] starts, so it receives every event of the
     * turn. If the turn continues an existing task ([RequestContext.task] is not `null`):
     * - the processor starts in task mode, see [SessionEventProcessor];
     * - the stored task is read atomically with the registration of [Session.events] (with the history limited to
     *   the request's [ai.koog.a2a.model.SendMessageConfiguration.historyLength] and all artifacts) and kept as the
     *   session's task snapshot.
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
     */
    public suspend fun startTurn(
        context: RequestContext<SendMessageRequest>,
        block: suspend (SessionEventProcessor) -> Unit,
    ): Session {
        val taskId = context.taskId
        val contextId = context.contextId

        val turnView = CompletableDeferred<Flow<Event>>()
        val taskSnapshot = CompletableDeferred<Task?>()
        val taskExists = context.task != null

        /*
         Under the registry lock only reserve the task and pin the stream. The turn view is registered under the stream
         lock afterwards: the stream lock can be held for long by a fan-out waiting for a slow collector, and the
         registry lock must not wait for it. Meanwhile the turn is already visible to getSession (so a concurrent
         join() works) and blocks other startTurn calls for the task, but nothing can publish for it: the agent job is
         lazy and is started only by monitorTurn, which is launched after the registration.
         */
        val (turn, stream, turnId) = registryMutex.withLock {
            check(taskId !in turns) {
                "Session for taskId '$taskId' already runs."
            }

            val stream = acquireLocked(taskId)
            val turnId = nextTurnId++

            val processor = SessionEventProcessor(
                contextId = contextId,
                taskId = taskId,
                stream = stream,
                turnId = turnId,
                taskExists = taskExists,
            )

            val session = Session(
                taskId = taskId,
                contextId = contextId,
                agentJob = coroutineScope.async(start = CoroutineStart.LAZY) { block(processor) },
                eventProcessor = processor,
                events = flow { emitAll(turnView.await()) },
                completion = Job(),
                taskSnapshot = taskSnapshot,
            )

            val turn = Turn(session)
            turns[taskId] = turn

            Triple(turn, stream, turnId)
        }
        val session = turn.session

        try {
            val (snapshot, channel) = if (taskExists) {
                stream.openTurnView(turnId) {
                    taskStorage.get(
                        taskId,
                        historyLength = context.params.configuration?.historyLength,
                        includeArtifacts = true,
                    )
                }
            } else {
                null to stream.openTurnView(turnId)
            }

            taskSnapshot.complete(snapshot)
            turnView.complete(channel.consumeAsFlow())
            turn.registered.complete(true)
        } catch (e: Throwable) {
            // Registration failed or the caller was cancelled: undo the reservation.
            withContext(NonCancellable) {
                registryMutex.withLock {
                    if (turns[taskId] === turn) turns -= taskId
                }

                // The agent job never started; cancel it so it doesn't linger as a child of the scope.
                session.agentJob.cancel()
                turnView.completeExceptionally(e)
                taskSnapshot.completeExceptionally(e)
                turn.registered.complete(false)
                session.completion.complete()
                release(stream)
            }
            throw e
        }

        coroutineScope.launch {
            monitorTurn(session, stream, turnId)
        }

        return session
    }

    /**
     * Waits for the turn's agent job to complete (starting it, if needed), ends the turn, and cleans up.
     */
    private suspend fun monitorTurn(session: Session, stream: TaskEventStream, turnId: Long) {
        try {
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

            /*
             Wait for any withTaskProcessor block (e.g. cancellation) that may still publish through this turn's
             processor, then close the processor and the turn view, and remove the session.
             */
            stream.processorGate.withLock {
                session.eventProcessor.close()
                stream.endTurn(turnId, session.contextId, failure)
                registryMutex.withLock { turns -= session.taskId }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Failed to finish the turn of taskId='${session.taskId}'" }
        } finally {
            withContext(NonCancellable) {
                // Make sure the requester's view ends even if finishing the turn failed or was cancelled.
                stream.closeTurnView(turnId)
                registryMutex.withLock {
                    if (turns[session.taskId]?.session === session) turns -= session.taskId
                }
                release(stream)
            }
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
     * publishes reaches the turn's requester as well. Otherwise, [block] receives a new processor and `null`.
     * Either way, the events [block] publishes reach every [subscribe] stream of the task.
     *
     * [block] may cancel and join the agent job, but must not wait for the turn to end (e.g. with [Session.join]),
     * since the turn can't end while [block] runs.
     *
     * @param taskId The task id.
     * @param contextId The context id of the task, used for the new processor when no turn is running.
     * @param block The action to run.
     * @return The result of [block].
     */
    public suspend fun <T> withTaskProcessor(
        taskId: String,
        contextId: String,
        block: suspend (processor: SessionEventProcessor, agentJob: Deferred<Unit>?) -> T,
    ): T {
        val stream = acquire(taskId)

        try {
            return stream.processorGate.withLock {
                val session = findRegisteredSession(taskId)

                val processor = session?.eventProcessor ?: SessionEventProcessor(
                    contextId = contextId,
                    taskId = taskId,
                    stream = stream,
                    turnId = null,
                )

                block(processor, session?.agentJob)
            }
        } finally {
            release(stream)
        }
    }

    /**
     * Returns the session of the running turn of the given task id, if any.
     */
    public suspend fun getSession(taskId: String): Session? = registryMutex.withLock {
        turns[taskId]?.session
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

    /**
     * Returns the session of the running turn of [taskId] once its turn view is registered, or `null` if there is no
     * turn. Waits for a turn that is still being started, so that whatever the caller publishes through the session's
     * processor reaches the turn's [Session.events]. Must not be called while holding the registry lock.
     */
    private suspend fun findRegisteredSession(taskId: String): Session? {
        while (true) {
            val turn = registryMutex.withLock { turns[taskId] } ?: return null

            // False if the turn failed to start; it has been removed by then, so look again.
            if (turn.registered.await()) return turn.session
        }
    }

    private suspend fun acquire(taskId: String): TaskEventStream = registryMutex.withLock {
        acquireLocked(taskId)
    }

    private fun acquireLocked(taskId: String): TaskEventStream {
        val entry = streams.getOrPut(taskId) {
            StreamEntry(TaskEventStream(taskId, taskStorage, idGenerator))
        }
        entry.holders++

        return entry.stream
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
