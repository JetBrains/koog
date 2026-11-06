package ai.koog.a2a.server.tasks

import ai.koog.a2a.exceptions.A2AInvalidParamsException
import ai.koog.a2a.model.GetTaskRequest
import ai.koog.a2a.model.ListTasksRequest
import ai.koog.a2a.model.ListTasksResponse
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskEvent
import ai.koog.a2a.server.exceptions.TaskOperationException
import ai.koog.a2a.transport.ServerCallContext

/**
 * Storage of tasks.
 *
 * - Scoping: storages may scope every method that takes a [ServerCallContext] by it, e.g. by an owner in
 *   [ServerCallContext.state] and by [ServerCallContext.tenant]. A task not visible to the caller is reported as not
 *   found.
 * - Visibility: a task stays visible to the context that created or updated it, at least for the rest of that turn
 *   or cancellation.
 *
 * Implementations must ensure concurrency safety.
 */
public interface TaskStorage {
    /**
     * Serves `GetTask`, and the server's reads of a task for a caller: follow-ups, cancellation, subscriptions, the
     * blocking SendMessage result, referenced tasks.
     *
     * @param request the task to read and its representation
     * @param callContext the context of the call
     * @return the task, or `null` if it doesn't exist or isn't visible to [callContext]
     */
    public suspend fun get(request: GetTaskRequest, callContext: ServerCallContext): Task?

    /**
     * The server's own reads of a task that need only part of it, e.g. to check that the task exists. The default
     * reads it with [get] and trims it with [project]; override it to apply [projection] in the query.
     *
     * @param taskId the task ID
     * @param projection the parts of the task to return
     * @param callContext the context of the call
     * @return the task, or `null` if it doesn't exist or isn't visible to [callContext]
     */
    public suspend fun get(taskId: String, projection: TaskProjection, callContext: ServerCallContext): Task? {
        val request = GetTaskRequest(id = taskId, historyLength = projection.historyLength, tenant = callContext.tenant)
        return get(request, callContext)?.project(projection)
    }

    /**
     * Serves `ListTasks`.
     *
     * @param request the filters, the page and the representation of the tasks
     * @param callContext the context of the call
     * @return the requested page of the tasks visible to [callContext]
     * @throws A2AInvalidParamsException if the page token is invalid
     */
    public suspend fun list(request: ListTasksRequest, callContext: ServerCallContext): ListTasksResponse

    /**
     * Persists an event of a turn, or the cancellation forced by CancelTask. A [Task] creates or replaces the task; a
     * status update replaces the status, moving the replaced status message into the history unless a message with
     * its messageId is there, and merges metadata; an artifact update adds, replaces or appends to an artifact.
     *
     * A storage may trim or redact what it keeps, provided reads still return the id, contextId and status state as
     * last written, and kept messages keep their messageId.
     *
     * @param event the event to apply
     * @param callContext the context of the turn or the CancelTask; an owner-aware storage may record the owner from it
     * @throws TaskOperationException if the event can't be applied, e.g. a non-[Task] event for a missing task
     */
    public suspend fun update(event: TaskEvent, callContext: ServerCallContext)

    /**
     * Persists the user message of a follow-up turn, before its first status or artifact update: unless a message
     * with its messageId is in the history, moves the status message into the history, appends [message] and clears
     * the status message. Does nothing if the task doesn't exist or isn't visible to [callContext].
     *
     * The default reads the task with [get] and writes it back with [update]; override it to do it atomically.
     *
     * @param taskId the task ID
     * @param message the user message
     * @param callContext the context of the turn
     */
    public suspend fun appendHistory(taskId: String, message: Message, callContext: ServerCallContext) {
        val task = get(GetTaskRequest(id = taskId, tenant = callContext.tenant), callContext) ?: return
        task.withAppendedHistory(message)?.let { update(it, callContext) }
    }

    /**
     * Service method, not scoped and never called by the server.
     *
     * @param taskIds the task IDs
     * @return the full tasks of [taskIds], in that order, skipping missing ones
     */
    public suspend fun getAll(taskIds: List<String>): List<Task>

    /**
     * Service method, not scoped and never called by the server: deletes the tasks of [taskIds]. Missing ones are
     * ignored.
     *
     * @param taskIds the task IDs
     * @throws TaskOperationException if an existing task can't be deleted
     */
    public suspend fun deleteAll(taskIds: List<String>)
}
