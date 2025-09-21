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

/**
 * Manages a set of active instances of [Session], sends push notifications if configured after each session completes.
 * Automatically closes and removes the session when agent job is completed (whether successfully or not).
 *
 * Additionally, if push notifications are configured, after each task session completes, push notifications are sent with
 * the current task state.
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
    private val rwLock = RWLock()

    /**
     * Adds a session to a set of active sessions.
     * If the first event in the session events stream is of type [TaskEvent], the session is added to the task sessions map too.
     *
     * Handles cleanup by closing and removing the session when it is completed (whether successfully or not).
     *
     * @param session The session to add.
     */
    public suspend fun addSession(session: Session) {
        rwLock.withWriteLock {
            check(session.taskId !in sessions) {
                "SessionEventProcessor for taskId '${session.taskId}' already exists."
            }

            sessions[session.taskId] = session
        }

        // Monitor for agent job completion to send push notifications and remove session from the map.
        coroutineScope.launch {
            val firstEvent = session.events.firstOrNull()

            // Wait for agent job to complete
            session.agentJob.join()

            // Send push notifications with the current state of the task, after the session completion, if configured.
            if (firstEvent is TaskEvent && pushSender != null && pushConfigStorage != null) {
                val task = taskStorage.get(session.taskId, historyLength = 0, includeArtifacts = false)

                if (task != null) {
                    pushConfigStorage.getAll(session.taskId).forEach { config ->
                        pushSender.send(config, task)
                    }
                }
            }

            // Close the session completely and remove it from the sessions map.
            rwLock.withWriteLock {
                sessions -= session.taskId
                session.close()
            }
        }
    }

    /**
     * Returns the session for the given task id, if any.
     */
    public suspend fun sessionForTask(taskId: String): Session? = rwLock.withReadLock {
        sessions[taskId]
    }

    /**
     * Returns the number of active sessions.
     */
    public suspend fun activeSessions(): Int = rwLock.withReadLock {
        sessions.size
    }
}
