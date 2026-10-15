package ai.koog.a2a.server.session

import ai.koog.a2a.exceptions.A2AInternalErrorException
import ai.koog.a2a.exceptions.A2ATaskNotFoundException
import ai.koog.a2a.exceptions.A2AUnsupportedOperationException
import ai.koog.a2a.model.Event
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskEvent
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatus
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.server.tasks.TaskStorage
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
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
 * Each subscriber has its own bounded channel of [bufferCapacity] events. When a subscriber falls behind and its
 * channel is full, it is evicted: its channel is closed with an [A2AInternalErrorException] (it still receives the
 * events already buffered) and it no longer receives events. Neither the publisher nor the other subscribers wait
 * for it. A subscriber whose consumer was cancelled is dropped silently.
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
 * @param bufferCapacity the capacity of each subscriber's buffer
 */
internal class TaskEventStream(
    val taskId: String,
    private val taskStorage: TaskStorage,
    private val bufferCapacity: Int,
) {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    private class Subscriber(
        val channel: Channel<Event>,
        /**
         * Turn id for turn views, `null` for task subscribers.
         */
        val turnId: Long?,
    )

    /**
     * The stream lock: guards [taskStorage] writes for this task, fan-out, and [subscribers].
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
     * Removes the subscriber reading from [channel], if it is still registered, and closes its channel.
     */
    suspend fun unsubscribe(channel: ReceiveChannel<Event>): Unit = mutex.withLock {
        subscribers.removeAll { subscriber ->
            (subscriber.channel === channel).also { if (it) subscriber.channel.close() }
        }
    }

    /**
     * Publishes [event]: a [TaskEvent] is persisted and delivered to all subscribers, a
     * [ai.koog.a2a.model.Message] only to the view of turn [turnId]. A terminal [TaskEvent] closes all subscribers.
     *
     * Returns once the event is persisted and delivered to the subscribers' buffers.
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

    private fun register(turnId: Long?): ReceiveChannel<Event> {
        val channel = Channel<Event>(bufferCapacity)
        subscribers += Subscriber(channel, turnId)
        return channel
    }

    private inline fun fanOut(event: Event, filter: (Subscriber) -> Boolean) {
        val iterator = subscribers.iterator()

        while (iterator.hasNext()) {
            val subscriber = iterator.next()
            if (!filter(subscriber)) continue

            val result = subscriber.channel.trySend(event)

            when {
                result.isSuccess -> Unit

                // The consumer has gone away, e.g. its collector was cancelled.
                result.isClosed -> iterator.remove()

                // The buffer is full: evict the slow subscriber without affecting anyone else.
                else -> {
                    logger.warn { "Evicting slow subscriber of task '$taskId': buffer of $bufferCapacity events is full" }

                    subscriber.channel.close(
                        A2AInternalErrorException(
                            "Subscriber of task '$taskId' was too slow to consume events and has been disconnected"
                        )
                    )
                    iterator.remove()
                }
            }
        }
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
