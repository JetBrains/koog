package ai.koog.a2a.server.tasks

import ai.koog.a2a.exceptions.A2AInvalidParamsException
import ai.koog.a2a.model.GetTaskRequest
import ai.koog.a2a.model.ListTasksRequest
import ai.koog.a2a.model.ListTasksResponse
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskEvent
import ai.koog.a2a.server.exceptions.TaskOperationException
import ai.koog.a2a.transport.ServerCallContext

/**
 * Storage interface for managing tasks and their lifecycle events.
 *
 * Implementations must ensure concurrency safety.
 */
public interface TaskStorage {
    /**
     * Serves the `GetTask` RPC: retrieves a task by [GetTaskRequest.id].
     *
     * Contract:
     * - The returned task always includes [Task.artifacts].
     * - [GetTaskRequest.historyLength] must be honoured with the rules of [project]: `null` returns the whole history,
     *   `0` returns `history = null` (not an empty list), `N` returns at most the `N` most recent messages.
     *   [ai.koog.a2a.server.A2AServer] validates that the value is not negative before calling this method.
     * - [GetTaskRequest.tenant] and [context] may be used by multi-tenant or authorization-aware storages to scope the
     *   lookup (a task that is not visible to the caller must be reported as not found). The in-memory implementation
     *   ignores both.
     *
     * For internal reads that do not originate from the `GetTask` RPC, use the overload that takes a task ID.
     *
     * @param request the `GetTask` request
     * @param context the context of the call
     * @return the task, or `null` if it is not found (or not visible to the caller)
     */
    public suspend fun get(request: GetTaskRequest, context: ServerCallContext): Task?

    /**
     * Serves the `ListTasks` RPC: returns a page of tasks matching [request].
     *
     * Fields of [ListTasksRequest] that the storage must honour:
     * - [ListTasksRequest.contextId]: when set, only tasks of this context are returned.
     * - [ListTasksRequest.status]: when set and not [ai.koog.a2a.model.TaskState.TASK_STATE_UNSPECIFIED], only tasks
     *   whose current status state equals it are returned. `null` and `TASK_STATE_UNSPECIFIED` mean no filtering.
     * - [ListTasksRequest.statusTimestampAfter]: when set, only tasks whose status timestamp is at or after it
     *   are returned. Tasks without a status timestamp never match.
     * - All filters are combined with logical AND.
     * - Ordering: by status timestamp, newest first. The ordering must be deterministic and stable across pages, so
     *   implementations must define a tie-breaker (see [InMemoryTaskStorage] for the reference ordering).
     * - [ListTasksRequest.pageToken]: an opaque token previously returned in [ListTasksResponse.nextPageToken].
     *   Its format is defined by the storage. An invalid token must be reported with [A2AInvalidParamsException].
     * - [ListTasksRequest.pageSize]: `null` means the default of 50. The effective page size must be echoed in
     *   [ListTasksResponse.pageSize].
     * - [ListTasksRequest.historyLength] and [ListTasksRequest.includeArtifacts]: apply them to every returned task
     *   with [project]. `includeArtifacts = null` is treated as `false` (artifacts are omitted), `historyLength = null`
     *   returns the whole history, `0` returns `history = null`.
     * - [ListTasksRequest.tenant] and [context] may be used by multi-tenant or authorization-aware storages to scope
     *   the result (including [ListTasksResponse.totalSize] and paging). The in-memory implementation ignores both.
     *
     * Response requirements: [ListTasksResponse.totalSize] is the number of tasks matching the filters before
     * pagination, and [ListTasksResponse.nextPageToken] must be the empty string on the last page (the field is
     * always present on the wire).
     *
     * What [ai.koog.a2a.server.A2AServer] validates before calling this method: [ListTasksRequest.pageSize] is in
     * `1..100` when present and [ListTasksRequest.historyLength] is not negative. Storages are not required to
     * repeat these checks.
     *
     * @param request the `ListTasks` request
     * @param context the context of the call
     * @return the requested page of tasks
     * @throws A2AInvalidParamsException if the page token is invalid
     */
    public suspend fun list(request: ListTasksRequest, context: ServerCallContext): ListTasksResponse

    /**
     * Retrieves a task by ID. Intended for internal use (cancellation, sessions, push notifications, agents), as
     * opposed to the `GetTask` RPC served by [get] with a [GetTaskRequest].
     *
     * @param taskId the unique task identifier
     * @param historyLength the maximum number of messages in conversation history to include in the response
     * Set to `null` to include all messages (default), `0` for no history. See [project].
     * @param includeArtifacts whether to include artifacts in the response. Default is `true`.
     *
     * @return the task or null if not found
     */
    public suspend fun get(
        taskId: String,
        historyLength: Int? = null,
        includeArtifacts: Boolean = true
    ): Task?

    /**
     * Retrieves multiple tasks by their IDs.
     *
     * @param taskIds list of task identifiers
     * @param historyLength the maximum number of messages in conversation history to include in the response
     * Set to `null` to include all messages (default), `0` for no history. See [project].
     * @param includeArtifacts whether to include artifacts in the response. Default is `true`.
     * @return list of found tasks (may be fewer than requested)
     */
    public suspend fun getAll(
        taskIds: List<String>,
        historyLength: Int? = null,
        includeArtifacts: Boolean = true
    ): List<Task>

    /**
     * Retrieves all tasks associated with a specific context ID.
     *
     * @param contextId context identifier
     * @param historyLength the maximum number of messages in conversation history to include in the response
     * Set to `null` to include all messages (default), `0` for no history. See [project].
     * @param includeArtifacts whether to include artifacts in the response. Default is `true`.
     * @return A list of tasks that match the specified context ID.
     */
    public suspend fun getByContext(
        contextId: String,
        historyLength: Int? = null,
        includeArtifacts: Boolean = true
    ): List<Task>

    /**
     * Updates task state based on an event, or creates the task if it doesn't exist.
     *
     * - A [Task] is stored as a new task, or replaces the stored task entirely (status, history, artifacts, and
     *   metadata). [ai.koog.a2a.server.A2AServer] never passes an agent's [Task] for a task that already exists (such
     *   events are ignored), but it does use the replacement to persist the user message of a follow-up turn: it reads
     *   the full stored task, moves [ai.koog.a2a.model.TaskStatus.message] into [Task.history], appends the user
     *   message, and writes the result back. Implementations must therefore store the given task as is.
     * - A [ai.koog.a2a.model.TaskStatusUpdateEvent] replaces the status of the existing task. If the status
     *   that is replaced has a message, that message is moved into [Task.history] (appended to the end), unless the
     *   history already contains a message with the same [ai.koog.a2a.model.Message.messageId], in which case it is
     *   not added again. Metadata is merged.
     * - A [ai.koog.a2a.model.TaskArtifactUpdateEvent] adds, replaces, or appends to an artifact of the existing task.
     *
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

/**
 * A specialized wrapper around [TaskStorage] for providing access to the tasks within a specific context.
 *
 * @param contextId the unique identifier for the current context
 * @param taskStorage the underlying task storage implementation
 * @see [TaskStorage]
 */
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
        historyLength: Int? = null,
        includeArtifacts: Boolean = true
    ): Task? = taskStorage.get(taskId, historyLength, includeArtifacts)

    /**
     * Retrieves multiple tasks by their IDs.
     *
     * @see [TaskStorage.getAll]
     */
    public suspend fun getAll(
        taskIds: List<String>,
        historyLength: Int? = null,
        includeArtifacts: Boolean = true
    ): List<Task> = taskStorage.getAll(taskIds, historyLength, includeArtifacts)

    /**
     * Retrieves all tasks associated with the current context ID.
     *
     * @see [TaskStorage.getByContext]
     */
    public suspend fun getByContext(
        historyLength: Int? = null,
        includeArtifacts: Boolean = true
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
