package ai.koog.a2a.server.session

import ai.koog.a2a.annotations.InternalA2AApi
import ai.koog.a2a.model.TaskEvent
import ai.koog.a2a.server.notifications.PushNotificationConfigStorage
import ai.koog.a2a.server.notifications.PushNotificationSender
import ai.koog.a2a.server.tasks.TaskStorage
import ai.koog.a2a.utils.RWLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Manages a set of active instances of [Session], sends push notifications if configured after each session completes.
 * Automatically closes and removes the session when agent job is completed (whether successfully or not).
 *
 * Additionally, if push notifications are configured, after each task session completes, push notifications are sent with
 * the current task state.
 *
 * Provides the ability to lock a task id.
 *
 * @param coroutineScope The scope in which the monitoring jobs will be launched.
 * @param taskStorage The storage for tasks.
 * @param pushConfigStorage The storage for push notification configurations.
 * @param pushSender The push notification sender.
 */
@OptIn(InternalA2AApi::class)
public class SessionManager(
    private val coroutineScope: CoroutineScope,
    private val taskStorage: TaskStorage,
    private val pushConfigStorage: PushNotificationConfigStorage? = null,
    private val pushSender: PushNotificationSender? = null,
) {

    /**
     * Map of task id to session. All sessions have task id associated with them, even if the task won't be created.
     */
    private val sessions = mutableMapOf<String, Session>()
    private val sessionsRwLock = RWLock()

    private val taskMutexes = mutableMapOf<String, Mutex>()
    private val taskMutexesLock = Mutex()

    /**
     * Adds a session to a set of active sessions.
     * Handles cleanup by closing and removing the session when it is completed (whether successfully or not).
     * Sends push notifications if configured after each session completes.
     *
     * @param session The session to add.
     * @throws IllegalArgumentException if a session for the same task id already exists.
     */
    public suspend fun addSession(session: Session) {
        sessionsRwLock.withWriteLock {
            check(session.taskId !in sessions) {
                "SessionEventProcessor for taskId '${session.taskId}' already exists."
            }

            sessions[session.taskId] = session
        }

        // Monitor for agent job completion to send push notifications and remove session from the map.
        coroutineScope.launch {
            val firstEvent = session.events.firstOrNull()

            // Wait for the agent job to finish
            session.agentJob.join()

            /*
             Check and wait if the task lock is free (e.g., there's a cancellation request for this task running now and still publishing some events).
             Then remove it from the sessions map.
             */
            withTaskLock(session.taskId) {
                sessionsRwLock.withWriteLock {
                    session.cancel()
                    sessions -= session.taskId
                }
            }

            // Send push notifications with the current state of the task, after the session completion, if configured.
            if (firstEvent is TaskEvent && pushSender != null && pushConfigStorage != null) {
                val task = taskStorage.get(session.taskId, historyLength = 0, includeArtifacts = false)

                if (task != null) {
                    pushConfigStorage.getAll(session.taskId).forEach { config ->
                        pushSender.send(config, task)
                    }
                }
            }
        }
    }

    /**
     * Returns the session for the given task id, if it exists.
     */
    public suspend fun getSession(taskId: String): Session? = sessionsRwLock.withReadLock {
        sessions[taskId]
    }

    /**
     * Returns the number of active sessions.
     */
    public suspend fun activeSessions(): Int = sessionsRwLock.withReadLock {
        sessions.size
    }

    /**
     * Acquires a lock for the specified task ID.
     * Useful for maintaining concurrency safety in task-related operations.
     *
     * @param taskId The unique identifier of the task to be locked.
     */
    public suspend fun taskLock(taskId: String) {
        val mutex = taskMutexesLock.withLock {
            taskMutexes.getOrPut(taskId) { Mutex() }
        }
        mutex.lock()
    }

    /**
     * Releases the lock for the specified task ID.
     * Useful for maintaining concurrency safety in task-related operations.
     *
     * @param taskId The unique identifier of the task to be unlocked.
     * @throws IllegalStateException if the lock for the task cannot be released.
     */
    public suspend fun taskUnlock(taskId: String) {
        val mutex = taskMutexesLock.withLock {
            taskMutexes[taskId]
        } ?: throw IllegalStateException("Task '$taskId' was never locked")

        if (!mutex.isLocked) {
            throw IllegalStateException("Task '$taskId' is not currently locked")
        }

        mutex.unlock()

        // Clean up unused mutexes
        taskMutexesLock.withLock {
            if (!mutex.isLocked && taskMutexes[taskId] === mutex) {
                taskMutexes.remove(taskId)
            }
        }
    }

    /**
     * Returns true if the task ID is locked, false otherwise.
     */
    public suspend fun isTaskLocked(taskId: String): Boolean {
        return taskMutexesLock.withLock {
            taskMutexes[taskId]?.isLocked == true
        }
    }
}

/**
 * Executes the given block of code while holding a lock for the specified task ID.
 * Useful for maintaining concurrency safety in task-related operations.
 *
 * @param taskId The ID of the task to be locked.
 * @param action The block of code to be executed.
 * @return The result of [action]
 */
@OptIn(ExperimentalContracts::class)
public suspend inline fun <T> SessionManager.withTaskLock(taskId: String, action: suspend () -> T): T {
    contract {
        callsInPlace(action, InvocationKind.EXACTLY_ONCE)
    }

    taskLock(taskId)
    return try {
        action()
    } finally {
        taskUnlock(taskId)
    }
}
