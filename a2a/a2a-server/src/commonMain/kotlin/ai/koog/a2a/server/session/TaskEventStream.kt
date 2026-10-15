package ai.koog.a2a.server.session

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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/**
 * Per-task event stream: the single place where events of one task are persisted and broadcast.
 *
 * One instance exists per task while the task has a running turn, an active [SessionEventProcessor] (e.g. during
 * cancellation), or at least one subscriber; [SessionManager] creates it lazily and drops it when none of these
 * hold. The stream keeps no task state of its own: every read goes to [taskStorage], so a dropped stream can be
 * recreated at any time, e.g. for an idle input-required task.
 *
 * Every operation runs under [mutex] (the "stream lock"), which guards the storage write, the fan-out, and the
 * subscriber set together. As a result:
 * - all subscribers observe events in the order they were persisted;
 * - [subscribe] reads the snapshot and registers the subscriber atomically, so a subscriber neither misses nor
 *   duplicates an event that is persisted concurrently.
 *
 * Delivery is back-pressured: every subscriber has its own rendezvous channel, and the fan-out suspends, still under
 * the stream lock, until each subscriber has accepted the event. A slow subscriber therefore slows the publisher
 * (the agent), the other subscribers, and any operation waiting for the stream lock ([subscribe], [openTurnView],
 * [endTurn], other publishers); no event is ever dropped and no subscriber is evicted. Subscribers receive an event
 * in the order they were registered.
 *
 * A subscriber whose consumer has gone away must cancel its channel (see [unsubscribe]). Cancelling does not need
 * the stream lock, so it works even while the fan-out is suspended on that very subscriber: the pending delivery fails
 * and the fan-out drops the subscriber and goes on. If the publisher itself is cancelled while the fan-out is
 * suspended, the event is already persisted but the subscribers not reached yet do not receive it.
 *
 * There are two kinds of subscribers:
 * - task subscribers (SubscribeToTask), which receive every [TaskEvent] and end only when the task reaches a terminal
 *   state;
 * - turn views, which receive every [TaskEvent] plus the [ai.koog.a2a.model.Message] of their own turn, and end
 *   either at a terminal state or when [endTurn] is called for their turn. Closing the turn view channel under the
 *   stream lock after the turn's last event is the in-band end-of-turn marker.
 *
 * @param taskId the id of the task this stream belongs to
 * @param taskStorage the storage where task events are persisted
 * @param idGenerator generates the id of the message attached to the failure status
 */
internal class TaskEventStream(
    val taskId: String,
    private val taskStorage: TaskStorage,
    private val idGenerator: IdGenerator,
) {
    private companion object {
        private const val FAILURE_MESSAGE = "Task failed due to an internal error"
    }

    private class Subscriber(
        val channel: Channel<Event>,
        /**
         * Turn id for turn views, `null` for task subscribers.
         */
        val turnId: Long?,
    )

    /**
     * The stream lock: guards [taskStorage] writes for this task, fan-out, and [subscribers]. Held while the fan-out
     * is suspended on a slow subscriber.
     */
    private val mutex = Mutex()

    private val subscribers = mutableListOf<Subscriber>()

    /**
     * Held by [SessionManager] while a [SessionEventProcessor] obtained through [SessionManager.withTaskProcessor]
     * is in use, and while a turn is being finished. It keeps a turn from being finished (and its processor closed)
     * while another party still publishes through that turn's processor, e.g. a cancellation handler.
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

        task to register(turnId = null)
    }

    /**
     * Registers the view of the turn [turnId]. Must be called before the turn publishes its first event.
     */
    suspend fun openTurnView(turnId: Long): ReceiveChannel<Event> = mutex.withLock {
        register(turnId)
    }

    /**
     * Atomically reads the current task with [lookup] and registers the view of the turn [turnId], so the snapshot
     * reflects exactly the events persisted before the view's first event. Must be called before the turn publishes
     * its first event.
     *
     * @param lookup reads the current task from the storage; called under the stream lock
     * @return the snapshot (as returned by [lookup]) and the turn view
     */
    suspend fun openTurnView(turnId: Long, lookup: suspend () -> Task?): Pair<Task?, ReceiveChannel<Event>> =
        mutex.withLock {
            lookup() to register(turnId)
        }

    /**
     * Stops the subscriber reading from [channel]: cancels the channel without taking the stream lock, so it never
     * waits for the fan-out, and a delivery to [channel] that is suspended right now fails immediately. The
     * subscriber is removed from the subscriber set by the next fan-out or registration, or by a terminal event.
     */
    fun unsubscribe(channel: ReceiveChannel<Event>) {
        channel.cancel()
    }

    /**
     * Publishes [event]: a [TaskEvent] is persisted and delivered to all subscribers, a
     * [ai.koog.a2a.model.Message] only to the view of turn [turnId]. A terminal [TaskEvent] closes all subscribers.
     *
     * Returns once the event is persisted and every addressed subscriber has accepted it, which can take as long as a
     * slow subscriber needs.
     *
     * @param event the event to publish
     * @param turnId the turn publishing the event, or `null` when it is published outside a turn
     */
    suspend fun publish(event: Event, turnId: Long?): Unit = mutex.withLock {
        if (event is TaskEvent) {
            taskStorage.update(event)
            fanOut(event) { true }

            if (event.isTerminal()) {
                closeAll()
            }
        } else {
            fanOut(event) { it.turnId != null && it.turnId == turnId }
        }
    }

    /**
     * Ends the turn [turnId]: closes its view after the last event of the turn.
     *
     * If the turn failed with [failure] (other than a cancellation) and the task exists but is not in a terminal
     * state, first marks the task [TaskState.TASK_STATE_FAILED] and delivers this update to all subscribers, which
     * then are closed.
     *
     * @param turnId the turn to end
     * @param contextId the context id of the task, used for the failure status update
     * @param failure the exception the turn failed with, or `null` if it completed normally
     */
    suspend fun endTurn(turnId: Long, contextId: String, failure: Throwable?): Unit = mutex.withLock {
        try {
            if (failure != null && failure !is CancellationException) {
                markFailed(contextId)
            }
        } finally {
            closeTurnViewLocked(turnId)
        }
    }

    /**
     * Closes the view of the turn [turnId], if it is still open. Idempotent.
     */
    suspend fun closeTurnView(turnId: Long): Unit = mutex.withLock {
        closeTurnViewLocked(turnId)
    }

    private suspend fun markFailed(contextId: String) {
        val task = taskStorage.get(taskId, historyLength = 0, includeArtifacts = false)

        if (task != null && !task.status.state.terminal) {
            val failed = TaskStatusUpdateEvent(
                taskId = taskId,
                contextId = task.contextId ?: contextId,
                status = TaskStatus(
                    state = TaskState.TASK_STATE_FAILED,
                    // Deliberately generic: exception details must not leak to clients.
                    message = Message(
                        messageId = idGenerator.generateMessageId(taskId, task.contextId ?: contextId),
                        role = Role.ROLE_AGENT,
                        parts = listOf(TextPart(FAILURE_MESSAGE)),
                        contextId = task.contextId ?: contextId,
                        taskId = taskId,
                    ),
                    timestamp = Clock.System.now(),
                ),
            )

            taskStorage.update(failed)
            fanOut(failed) { true }
            closeAll()
        }
    }

    private fun closeTurnViewLocked(turnId: Long) {
        subscribers.removeAll { subscriber ->
            (subscriber.turnId == turnId).also { if (it) subscriber.channel.close() }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun register(turnId: Long?): ReceiveChannel<Event> {
        // Drop subscribers whose consumers have gone away since the last fan-out.
        subscribers.removeAll { it.channel.isClosedForSend }

        val channel = Channel<Event>(Channel.RENDEZVOUS)
        subscribers += Subscriber(channel, turnId)
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
        // A channel cancelled by its consumer fails the send with a CancellationException too: tell it apart from
        // the cancellation of the publisher.
        currentCoroutineContext().ensureActive()
        false
    } catch (e: ClosedSendChannelException) {
        false
    }

    private fun closeAll() {
        subscribers.forEach { it.channel.close() }
        subscribers.clear()
    }

    private fun TaskEvent.isTerminal(): Boolean = when (this) {
        is Task -> status.state.terminal
        is TaskStatusUpdateEvent -> status.state.terminal
        else -> false
    }
}
