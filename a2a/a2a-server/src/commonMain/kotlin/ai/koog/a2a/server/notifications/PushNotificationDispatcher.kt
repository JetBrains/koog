package ai.koog.a2a.server.notifications

import ai.koog.a2a.model.TaskEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Sends a push notification for every persisted task event to every push notification config of its task.
 *
 * Events are queued per task, without bound, and sent by one worker per task with pending events, in enqueue order.
 * The configs are read from [configStorage] when an event is sent, so a deleted config gets no later events and a new
 * one may also get events queued before it was created. Each event is sent to all configs concurrently, and the next
 * event of the task only after that, so every config receives the events of its task in order. Tasks don't wait for
 * each other.
 *
 * Failures of [sender] are logged and skipped, without retries. Queued events are dropped when [scope] is cancelled.
 *
 * @param scope the scope the workers run in
 * @param configStorage the storage of the push notification configs
 * @param sender sends a notification to a single config
 */
internal class PushNotificationDispatcher(
    private val scope: CoroutineScope,
    private val configStorage: PushNotificationConfigStorage,
    private val sender: PushNotificationSender,
) {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    /** A leaf lock: held only for map and queue operations, never while suspending on anything else. */
    private val lock = Mutex()

    /** Pending events by task id. A key exists if and only if a worker for that task runs. */
    private val queues = mutableMapOf<String, ArrayDeque<TaskEvent>>()

    /**
     * Queues [event] of the task [taskId], starting a worker if the task has none. Waits only for the leaf lock, and
     * isn't interrupted by cancellation, so an event that has been persisted is never lost here.
     */
    suspend fun enqueue(taskId: String, event: TaskEvent): Unit = withContext(NonCancellable) {
        val newQueue = lock.withLock {
            val queue = queues[taskId]

            if (queue == null) {
                ArrayDeque(listOf(event)).also { queues[taskId] = it }
            } else {
                queue.addLast(event)
                null
            }
        }

        if (newQueue != null) {
            // ATOMIC: the worker runs even if the scope is already cancelled, so its finally removes the queue.
            @OptIn(DelicateCoroutinesApi::class)
            scope.launch(start = CoroutineStart.ATOMIC) { drain(taskId, newQueue) }
        }
    }

    /** Returns the number of tasks with a running worker. */
    internal suspend fun activeWorkers(): Int = lock.withLock { queues.size }

    private suspend fun drain(taskId: String, queue: ArrayDeque<TaskEvent>) {
        try {
            while (true) {
                currentCoroutineContext().ensureActive()

                // The emptiness check and the removal of the key are atomic, so a concurrent enqueue either lands in
                // this queue or starts a new worker.
                val event = lock.withLock {
                    queue.removeFirstOrNull() ?: run {
                        queues -= taskId
                        null
                    }
                } ?: return

                dispatch(taskId, event)
            }
        } finally {
            // Drops the queue if the worker was cancelled; once removed, the key may belong to a new worker.
            withContext(NonCancellable) {
                lock.withLock {
                    if (queues[taskId] === queue) queues -= taskId
                }
            }
        }
    }

    private suspend fun dispatch(taskId: String, event: TaskEvent) {
        val configs = try {
            configStorage.getAll(taskId)
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            logger.warn(e) { "Failed to load push notification configs for taskId='$taskId'" }
            return
        }

        coroutineScope {
            configs.forEach { config ->
                launch {
                    try {
                        sender.send(config, event)
                    } catch (e: Exception) {
                        // A CancellationException not caused by our own cancellation is a failure of the sender.
                        if (e is CancellationException) currentCoroutineContext().ensureActive()
                        logger.warn(e) {
                            "Failed to send push notification for taskId='$taskId' configId='${config.id}'"
                        }
                    }
                }
            }
        }
    }
}
