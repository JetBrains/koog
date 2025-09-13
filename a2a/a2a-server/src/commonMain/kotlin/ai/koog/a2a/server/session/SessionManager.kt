package ai.koog.a2a.server.session

import ai.koog.a2a.annotations.InternalA2AApi
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.TaskEvent
import ai.koog.a2a.utils.RWLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch

/**
 * Manages a set of active instances of [SessionEventProcessor].
 *
 * Each added processor is monitored for task id associated with this session, if any, i.e., the session is processing a task,
 * and if it is a task-related session, the processor is added to the task sessions map.
 *
 * Automatically removes the processor when the session is closed.
 *
 * @param coroutineScope The scope in which the monitoring jobs will be launched.
 */
@OptIn(InternalA2AApi::class)
public class SessionManager(
    private val coroutineScope: CoroutineScope,
) {
    private val allProcessors = mutableSetOf<SessionEventProcessor>()
    private val taskProcessors = mutableMapOf<String, SessionEventProcessor>()
    private val rwLock = RWLock()

    /**
     * Adds a session event processor to a set of active processors.
     * If the first event in the processor is of type [TaskEvent], the processor is added to the task sessions map too.
     * Handles cleanup by removing the processor when the session is closed.
     *
     * @param eventProcessor The session event processor to be added.
     */
    public fun addProcessor(eventProcessor: SessionEventProcessor) {
        coroutineScope.launch {
            // Check if the first event in the session processor is task related and add this processor to the task sessions map.
            when (val firstEvent = eventProcessor.events.first()) {
                is TaskEvent -> {
                    val taskId = firstEvent.taskId

                    rwLock.withWriteLock {
                        check(taskId !in taskProcessors) {
                            "SessionEventProcessor for taskId '${firstEvent.taskId}' already exists."
                        }

                        allProcessors += eventProcessor
                        taskProcessors[firstEvent.taskId] = eventProcessor
                    }

                    // Wait for the session to close and remove the processor from collections.
                    eventProcessor.events
                        .onCompletion {
                            rwLock.withWriteLock {
                                allProcessors -= eventProcessor
                                taskProcessors -= taskId
                            }
                        }
                        .collect()
                }

                is Message -> {
                    allProcessors += eventProcessor

                    // Wait for the session to close and remove the processor from collections.
                    eventProcessor.events
                        .onCompletion {
                            rwLock.withWriteLock {
                                allProcessors -= eventProcessor
                            }
                        }
                        .collect()
                }
            }
        }
    }

    /**
     * Returns the session event processor for the given task id, if any.
     */
    public suspend fun processorForTask(taskId: String): SessionEventProcessor? = rwLock.withReadLock {
        taskProcessors[taskId]
    }

    /**
     * Returns the number of active session event processors.
     */
    public suspend fun activeProcessors(): Int = rwLock.withReadLock {
        allProcessors.size
    }
}
