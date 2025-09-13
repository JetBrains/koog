package ai.koog.a2a.server.tasks

import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskEvent
import ai.koog.a2a.server.exceptions.TaskOperationException

/**
 * Storage interface for managing tasks and their lifecycle events.
 *
 * Implementations must ensure concurrency safety.
 */
public interface TaskStorage {
    /**
     * Retrieves a task by ID.
     *
     * @param taskId the unique task identifier
     * @param historyLength the maximum number of messages in conversation history to include in the response
     * Set to `null` to include all messages. Defaults to `0`.
     * @param includeArtifacts whether to include artifacts in the response. Default is `false`.
     *
     * @return the task or null if not found
     */
    public suspend fun get(
        taskId: String,
        historyLength: Int? = 0,
        includeArtifacts: Boolean = false
    ): Task?

    /**
     * Retrieves multiple tasks by their IDs.
     *
     * @param taskIds list of task identifiers
     * @param historyLength the maximum number of messages in conversation history to include in the response
     * Set to `null` to include all messages. Defaults to `0`.
     * @param includeArtifacts whether to include artifacts in the response. Default is `false`.
     * @return list of found tasks (may be fewer than requested)
     */
    public suspend fun getAll(
        taskIds: List<String>,
        historyLength: Int? = 0,
        includeArtifacts: Boolean = false
    ): List<Task>

    /**
     * Retrieves all tasks associated with a specific context ID.
     *
     * @param contextId context identifier
     * @param historyLength the maximum number of messages in conversation history to include in the response
     * Set to `null` to include all messages. Defaults to `0`.
     * @param includeArtifacts whether to include artifacts in the response. Default is `false`.
     * @return A list of tasks that match the specified context ID.
     */
    public suspend fun getByContext(
        contextId: String,
        historyLength: Int? = 0,
        includeArtifacts: Boolean = false
    ): List<Task>

    /**
     * Updates task state based on an event, or creates the task if it doesn't exist.
     *
     * When the event is a [Task], it will be stored as a new task or replace an existing one.
     * When the event is a status/artifact update, it modifies the existing task state.
     * All tasks must be created before they can be updated with the [Task] event first.
     * Attempts to send a non-[Task] [TaskEvent] for a non-existing task will result in error.
     *
     * @param event the update event to apply (creation or modification)
     * @throws TaskOperationException if the task cannot be created or updated, e.g. [TaskEvent] that is not [Task] is sent for non-existing task id
     */
    public suspend fun update(event: TaskEvent)

    /**
     * Deletes a task by ID.
     *
     * @param taskId the task identifier to delete
     * @throws TaskOperationException if the task cannot be deleted, e.g. it doesn't exist
     */
    public suspend fun delete(taskId: String)

    /**
     * Deletes multiple tasks by their IDs.
     *
     * @param taskIds list of task identifiers to delete
     * @throws TaskOperationException if some tasks cannot be deleted, e.g. they don't exist
     */
    public suspend fun deleteAll(taskIds: List<String>)
}

public class ContextTaskStorage(
    private val contextId: String,
    private val taskStorage: TaskStorage,
) {
    /**
     * Retrieves a task by ID.
     *
     * @see [TaskStorage.get]
     */
    public suspend fun get(
        taskId: String,
        historyLength: Int? = 0,
        includeArtifacts: Boolean = false
    ): Task? = taskStorage.get(taskId, historyLength, includeArtifacts)

    /**
     * Retrieves multiple tasks by their IDs.
     *
     * @see [TaskStorage.getAll]
     */
    public suspend fun getAll(
        taskIds: List<String>,
        historyLength: Int? = 0,
        includeArtifacts: Boolean = false
    ): List<Task> = taskStorage.getAll(taskIds, historyLength, includeArtifacts)

    /**
     * Retrieves all tasks associated with the current context ID.
     *
     * @see [TaskStorage.getByContext]
     */
    public suspend fun getByContext(
        historyLength: Int? = 0,
        includeArtifacts: Boolean = false
    ): List<Task> = taskStorage.getByContext(contextId, historyLength, includeArtifacts)

    /**
     * Deletes a task by ID, checking that it belongs to the current context ID.
     *
     * @see [TaskStorage.delete]
     */
    public suspend fun delete(taskId: String) {
        get(taskId, historyLength = 0, includeArtifacts = false)?.let { task ->
            require(task.contextId == contextId) {
                "contextId of the task requested to be deleted must be same as current contextId"
            }

            taskStorage.delete(taskId)
        }
    }

    /**
     * Deletes multiple tasks by their IDs, checking that they belong to the current context ID.
     *
     * @see [TaskStorage.deleteAll]
     */
    public suspend fun deleteAll(taskIds: List<String>) {
        getAll(taskIds, historyLength = 0, includeArtifacts = false).let { tasks ->
            require(tasks.all { it.contextId == contextId }) {
                "contextId of the tasks requested to be deleted must be same as current contextId"
            }

            taskStorage.deleteAll(taskIds)
        }
    }
}
