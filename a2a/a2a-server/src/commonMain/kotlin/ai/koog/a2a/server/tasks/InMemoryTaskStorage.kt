package ai.koog.a2a.server.tasks

import ai.koog.a2a.annotations.InternalA2AApi
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskArtifactUpdateEvent
import ai.koog.a2a.model.TaskEvent
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.server.exceptions.TaskOperationException
import ai.koog.a2a.utils.RWLock
import kotlinx.serialization.json.JsonObject

/**
 * In-memory implementation of [TaskStorage] using a thread-safe map.
 *
 * This implementation stores tasks in memory and provides concurrency safety through mutex locks.
 * Tasks are indexed by both ID and context ID for efficient retrieval.
 */
@OptIn(InternalA2AApi::class)
public class InMemoryTaskStorage : TaskStorage {
    private val tasks = mutableMapOf<String, Task>()
    private val tasksByContext = mutableMapOf<String, MutableSet<String>>()
    private val rwLock = RWLock()

    override suspend fun get(
        taskId: String,
        historyLength: Int?,
        includeArtifacts: Boolean
    ): Task? = rwLock.withReadLock {
        historyLength?.let {
            require(it >= 0) { "historyLength must be non-negative" }
        }

        val task = tasks[taskId] ?: return@withReadLock null
        // Need to modify the original full task object to remove some information
        val isModificationNeeded = historyLength != null || !includeArtifacts

        if (isModificationNeeded) {
            task.copy(
                history = if (historyLength != null) {
                    task.history?.takeLast(historyLength)
                } else {
                    task.history
                },
                artifacts = if (includeArtifacts) {
                    task.artifacts
                } else {
                    null
                }
            )
        } else {
            task
        }
    }

    override suspend fun getAll(
        taskIds: List<String>,
        historyLength: Int?,
        includeArtifacts: Boolean
    ): List<Task> = rwLock.withReadLock {
        taskIds.mapNotNull { taskId ->
            get(taskId, historyLength, includeArtifacts)
        }
    }

    override suspend fun getByContext(
        contextId: String,
        historyLength: Int?,
        includeArtifacts: Boolean
    ): List<Task> = rwLock.withReadLock {
        val contextTaskIds = tasksByContext[contextId] ?: emptySet()
        contextTaskIds.mapNotNull { taskId ->
            get(taskId, historyLength, includeArtifacts)
        }
    }

    override suspend fun update(event: TaskEvent): Unit = rwLock.withWriteLock {
        when (event) {
            is Task -> {
                val oldTask = tasks[event.id]

                if (oldTask != null && event.contextId != oldTask.contextId) {
                    throw TaskOperationException("Cannot change context for existing task: ${event.id}")
                }

                // Store or replace the task
                tasks[event.id] = event

                // Update context index
                tasksByContext.getOrPut(event.contextId) { mutableSetOf() }.add(event.id)
            }

            is TaskStatusUpdateEvent -> {
                val existingTask = tasks[event.taskId]
                    ?: throw TaskOperationException("Cannot update status for non-existing task: ${event.taskId}")

                val updatedTask = existingTask.copy(
                    status = event.status,
                    history = existingTask.status.message
                        ?.let { existingTask.history.orEmpty() + it }
                        ?: existingTask.history,
                    metadata = existingTask.metadata
                        ?.let { JsonObject(it + event.metadata.orEmpty()) }
                        ?: event.metadata,
                )

                tasks[event.taskId] = updatedTask
            }

            is TaskArtifactUpdateEvent -> {
                val existingTask = tasks[event.taskId]
                    ?: throw TaskOperationException("Cannot update artifact for non-existing task: ${event.taskId}")

                val currentArtifacts = existingTask.artifacts?.toMutableList() ?: mutableListOf()
                val existingArtifactIndex = currentArtifacts.indexOfFirst { it.artifactId == event.artifact.artifactId }

                if (existingArtifactIndex >= 0) {
                    val existingArtifact = currentArtifacts[existingArtifactIndex]

                    currentArtifacts[existingArtifactIndex] = if (event.append == true) {
                        existingArtifact.copy(parts = existingArtifact.parts + event.artifact.parts)
                    } else {
                        event.artifact
                    }
                } else {
                    currentArtifacts.add(event.artifact)
                }

                val updatedTask = existingTask.copy(
                    artifacts = currentArtifacts,
                    metadata = existingTask.metadata
                        ?.let { JsonObject(it + event.metadata.orEmpty()) }
                        ?: event.metadata
                )

                tasks[event.taskId] = updatedTask
            }
        }
    }

    override suspend fun delete(taskId: String): Unit = rwLock.withWriteLock {
        tasks.remove(taskId)?.let { task ->
            // Remove from context index
            tasksByContext[task.contextId]?.remove(taskId)
            if (tasksByContext[task.contextId]?.isEmpty() == true) {
                tasksByContext.remove(task.contextId)
            }
        }
    }

    override suspend fun deleteAll(taskIds: List<String>): Unit = rwLock.withWriteLock {
        taskIds.forEach { taskId ->
            tasks.remove(taskId)?.let { task ->
                // Remove from context index
                tasksByContext[task.contextId]?.remove(taskId)
                if (tasksByContext[task.contextId]?.isEmpty() == true) {
                    tasksByContext.remove(task.contextId)
                }
            }
        }
    }
}
