package ai.koog.a2a.server.session

import ai.koog.a2a.annotations.InternalA2AApi
import ai.koog.a2a.model.TaskEvent
import ai.koog.a2a.server.notifications.PushNotificationConfigStorage
import ai.koog.a2a.server.notifications.PushNotificationSender
import ai.koog.a2a.server.tasks.TaskStorage
import ai.koog.a2a.utils.KeyedMutex
import ai.koog.a2a.utils.RWLock
import ai.koog.a2a.utils.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * Manages a set of active instances of [LazySession], sends push notifications if configured after each session completes.
 * Automatically closes and removes the session when agent job is completed (whether successfully or not).
 *
 * Additionally, if push notifications are configured, after each task session completes, push notifications are sent with
 * the current task state.
 *
 * Provides the ability to lock a task id.
 *
 * @param coroutineScope The scope in which the monitoring jobs will be launched.
 * @param tasksMutex The mutex for locking specific task ids.
 * @param taskStorage The storage for tasks.
 * @param pushConfigStorage The storage for push notification configurations.
 * @param pushSender The push notification sender.
 */
@OptIn(InternalA2AApi::class)
public class SessionManager(
    private val coroutineScope: CoroutineScope,
    private val tasksMutex: KeyedMutex<String>,
    private val cancelKey: (String) -> String,
    private val taskStorage: TaskStorage,
    private val pushConfigStorage: PushNotificationConfigStorage? = null,
    private val pushSender: PushNotificationSender? = null,
) {

    /**
     * Map of task id to session. All sessions have task id associated with them, even if the task won't be created.
     */
    private val sessions = mutableMapOf<String, Session>()
    private val sessionsRwLock = RWLock()

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
                "Session for taskId '${session.taskId}' already runs."
            }

            sessions[session.taskId] = session
        }

        // Monitor for agent job completion to send push notifications and remove session from the map.
        coroutineScope.launch {
            val firstEvent = session.events.firstOrNull()

            // Wait for the agent job to finish
            session.agentJob.join()

            /*
             Check and wait if there's a cancellation request for this task running now and still publishing some events.
             Then remove it from the session map.
             */
            tasksMutex.withLock(cancelKey(session.taskId)) {
                sessionsRwLock.withWriteLock {
                    sessions -= session.taskId
                    session.cancelAndJoin()
                }
            }

            // Send push notifications with the current state of the task, after the session completion, if configured.
            coroutineScope.launch {
                if (firstEvent is TaskEvent && pushSender != null && pushConfigStorage != null) {
                    val task = taskStorage.get(session.taskId, historyLength = 0, includeArtifacts = false)

                    if (task != null) {
                        pushConfigStorage.getAll(session.taskId).forEach { config ->
                            try {
                                pushSender.send(config, task)
                            } catch (e: Exception) {
                                // TODO log error
                            }
                        }
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
}
