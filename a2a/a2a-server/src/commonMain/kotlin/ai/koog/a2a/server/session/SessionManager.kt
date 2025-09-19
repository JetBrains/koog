package ai.koog.a2a.server.session

import ai.koog.a2a.annotations.InternalA2AApi
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.TaskEvent
import ai.koog.a2a.server.notifications.PushNotificationConfigStorage
import ai.koog.a2a.server.notifications.PushNotificationSender
import ai.koog.a2a.server.tasks.TaskStorage
import ai.koog.a2a.utils.RWLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Manages a set of active instances of [Session], sends push notifications if configured after each session completes.
 *
 * Each session's event stream is monitored for task id associated with this session, if any, i.e., the session is processing a task,
 * and if it is a task-related session, it is added to the task sessions map.
 *
 * Automatically closes and removes the session when it is completed (whether successfully or not).
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
    private val allSessions = mutableSetOf<Session>()
    private val taskSessions = mutableMapOf<String, Session>()
    private val rwLock = RWLock()

    /**
     * Adds a session to a set of active sessions.
     * If the first event in the session events stream is of type [TaskEvent], the session is added to the task sessions map too.
     *
     * Handles cleanup by closing and removing the session when it is completed (whether successfully or not).
     *
     * @param session The session to add.
     */
    public fun addSession(session: Session) {
        coroutineScope.launch {
            // Check if the first event is task related and add this session to the task sessions map.
            when (val firstEvent = session.events.first()) {
                is TaskEvent -> {
                    val taskId = firstEvent.taskId

                    rwLock.withWriteLock {
                        check(taskId !in taskSessions) {
                            "SessionEventProcessor for taskId '${firstEvent.taskId}' already exists."
                        }

                        allSessions += session
                        taskSessions[firstEvent.taskId] = session
                    }

                    // Wait for the session to complete, then close and remove it from collections.
                    session.join()

                    rwLock.withWriteLock {
                        session.close()
                        allSessions -= session
                        taskSessions -= taskId
                    }

                    // Send push notifications with the current state of the task, after the session completion, if configured.
                    if (pushSender != null && pushConfigStorage != null) {
                        val task = taskStorage.get(taskId, historyLength = 0)

                        if (task != null) {
                            pushConfigStorage.getAll(taskId).forEach { config ->
                                pushSender.send(config, task)
                            }
                        }
                    }
                }

                is Message -> {
                    rwLock.withWriteLock {
                        allSessions += session
                    }

                    // Wait for the session to complete, then close and remove it from collection.
                    session.join()

                    rwLock.withWriteLock {
                        session.close()
                        allSessions -= session
                    }
                }
            }
        }
    }

    /**
     * Returns the session for the given task id, if any.
     */
    public suspend fun sessionForTask(taskId: String): Session? = rwLock.withReadLock {
        taskSessions[taskId]
    }

    /**
     * Returns the number of active sessions.
     */
    public suspend fun activeSessions(): Int = rwLock.withReadLock {
        allSessions.size
    }
}
