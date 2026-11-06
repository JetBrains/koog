package ai.koog.a2a.server.tasks

import ai.koog.a2a.exceptions.A2AInvalidParamsException
import ai.koog.a2a.model.GetTaskRequest
import ai.koog.a2a.model.ListTasksRequest
import ai.koog.a2a.model.ListTasksResponse
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskArtifactUpdateEvent
import ai.koog.a2a.model.TaskEvent
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.server.exceptions.TaskOperationException
import ai.koog.a2a.transport.ServerCallContext

/**
 * Storage interface for managing tasks and their lifecycle events.
 *
 * Every method but the service methods [getAll] and [deleteAll] takes, as its last parameter, the [ServerCallContext]
 * of the call it is made for. Methods serving an RPC take its request; the others take plain arguments and return
 * full data (callers trim it with [project]).
 *
 * Scoping: multi-tenant or authorization-aware storages may scope every method by the call context, e.g. by an owner
 * the server's subclass puts into [ServerCallContext.state], and by [ServerCallContext.tenant], which the transport
 * sets from the request (the requests' own `tenant` fields are wire fields; prefer the context's). A task that is not
 * visible to the caller must be reported as not found.
 *
 * Visibility: a task stays visible to the call context that created or updated it, at least for the rest of that
 * turn or cancellation. [ai.koog.a2a.server.A2AServer] reads the task back with that context, e.g. to apply a
 * follow-up, to mark it failed, to cancel it, or to answer a blocking SendMessage.
 *
 * Implementations must ensure concurrency safety.
 */
public interface TaskStorage {
    /**
     * Serves the `GetTask` RPC, and the server's own reads of a task made for a caller (follow-ups, cancellation,
     * failure handling, referenced tasks).
     *
     * Returns the task with all [Task.artifacts] and with [GetTaskRequest.historyLength] applied with [project];
     * [ai.koog.a2a.server.A2AServer] has checked that it isn't negative.
     *
     * @param request the `GetTask` request
     * @param callContext the context of the call, see [TaskStorage]
     * @return the task, or `null` if it doesn't exist or isn't visible to [callContext]
     */
    public suspend fun get(request: GetTaskRequest, callContext: ServerCallContext): Task?

    /**
     * Serves the `ListTasks` RPC: returns the page of tasks matching [request], as [ListTasksRequest] and
     * [ListTasksResponse] describe.
     *
     * The storage applies itself:
     * - the filters, the ordering and the paging; a `null` [ListTasksRequest.pageSize] means 50, and the size used is
     *   returned in [ListTasksResponse.pageSize];
     * - [ListTasksRequest.historyLength] and [ListTasksRequest.includeArtifacts] (`null` is `false`) to every task,
     *   with [project];
     * - the scoping by [callContext] (see [TaskStorage]), [ListTasksResponse.totalSize] and the paging included.
     *
     * The ordering needs a deterministic tie-breaker, so that pages are stable (see [InMemoryTaskStorage]). Page tokens
     * are opaque and storage-defined.
     *
     * [ai.koog.a2a.server.A2AServer] has validated [ListTasksRequest.pageSize] (`1..100`) and
     * [ListTasksRequest.historyLength] (not negative).
     *
     * @param request the `ListTasks` request
     * @param callContext the context of the call, see [TaskStorage]
     * @return the requested page of tasks
     * @throws A2AInvalidParamsException if the page token is invalid
     */
    public suspend fun list(request: ListTasksRequest, callContext: ServerCallContext): ListTasksResponse

    /**
     * Applies [event] to the stored task, or creates the task from a [Task] event.
     *
     * - A [Task] creates the task, or replaces the stored one (status, history, artifacts, metadata). The default
     *   [appendHistory] relies on the replacement.
     * - A [TaskStatusUpdateEvent] replaces the status. The replaced status message, if any, is appended to
     *   [Task.history] unless a message with its [Message.messageId] is there. Metadata is merged.
     * - A [TaskArtifactUpdateEvent] adds, replaces, or appends to an artifact.
     *
     * A storage may apply a retention or redaction policy (e.g. trim history, redact parts, drop artifacts or
     * metadata), provided later reads still return the task's id, contextId and status state as last written,
     * and every message kept has its original messageId. The server uses these for follow-ups,
     * cancellation, failure handling and history deduplication. All reads return the task as kept.
     *
     * [callContext] is the call the event is persisted for: the request of the turn that produced it, or the
     * CancelTask that forced the cancellation. The server has authorized it. An authorization-aware storage may
     * record the owner from it when the task is created, and the task must then be visible to that caller in [get]
     * and [list].
     *
     * @param event the event to apply
     * @param callContext the call the event is persisted for
     * @throws TaskOperationException if the event can't be applied, e.g. a non-[Task] event for a missing task
     */
    public suspend fun update(event: TaskEvent, callContext: ServerCallContext)

    /**
     * Adds [message] to the history of the task with [taskId], for internal use: the server persists the user message
     * of a follow-up turn with it.
     *
     * If the history already contains a message with the same [Message.messageId], the task is left unchanged.
     * Otherwise the current [ai.koog.a2a.model.TaskStatus.message], if any, is moved into [Task.history] (unless a
     * message with its id is already there), [message] is appended, and the status message is cleared. Nothing else
     * changes, apart from the storage's own retention policy (see [update]). [callContext] is the request of the turn
     * whose message it is. If the task doesn't exist (or isn't visible to [callContext]), nothing happens.
     *
     * The server never calls it concurrently with another write to the same task, but storages shared between
     * processes should apply it atomically. The default implementation reads the whole task with [get] and writes it
     * back with [update]; override it to avoid that cost.
     *
     * @param taskId the task identifier
     * @param message the message to add
     * @param callContext the request of the turn whose message it is
     */
    public suspend fun appendHistory(taskId: String, message: Message, callContext: ServerCallContext) {
        val task = get(GetTaskRequest(id = taskId, tenant = callContext.tenant), callContext) ?: return
        task.withAppendedHistory(message)?.let { update(it, callContext) }
    }

    /**
     * Returns the tasks with [taskIds] in full (whole history, all artifacts), in the order of [taskIds]; ids of tasks
     * that don't exist are skipped.
     *
     * For servicing the storage (e.g. migration, inspection), not for callers: it is not scoped by any call context,
     * and the server never calls it.
     *
     * @param taskIds the task identifiers
     * @return the tasks that exist
     */
    public suspend fun getAll(taskIds: List<String>): List<Task>

    /**
     * Deletes the tasks with [taskIds]. Idempotent: ids of tasks that don't exist are ignored.
     *
     * For servicing the storage (e.g. retention), not for callers: it is not scoped by any call context, and the
     * server never calls it.
     *
     * @param taskIds the task identifiers
     * @throws TaskOperationException if some existing tasks can't be deleted
     */
    public suspend fun deleteAll(taskIds: List<String>)
}
