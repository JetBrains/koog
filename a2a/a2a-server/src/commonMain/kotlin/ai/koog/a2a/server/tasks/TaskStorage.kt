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
 * Every method takes, as its last parameter, the [ServerCallContext] of the call it is made for. Methods serving an
 * RPC take its request; the others take plain arguments and return full data (callers trim it with [project]).
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
     * Serves the `GetTask` RPC: retrieves a task by [GetTaskRequest.id]. The server also uses it for its own reads of
     * a task made for a caller (follow-ups, cancellation, failure handling, referenced tasks).
     *
     * Contract:
     * - The returned task always includes [Task.artifacts].
     * - [GetTaskRequest.historyLength] must be honoured with the rules of [project]: `null` returns the whole history,
     *   `0` returns `history = null` (not an empty list), `N` returns at most the `N` most recent messages.
     *   [ai.koog.a2a.server.A2AServer] validates that the value is not negative before calling this method.
     * - The lookup may be scoped by [callContext], see [TaskStorage]: a task that is not visible to the caller is
     *   reported as not found.
     *
     * @param request the `GetTask` request
     * @param callContext the context of the call
     * @return the task, or `null` if it is not found (or not visible to the caller)
     */
    public suspend fun get(request: GetTaskRequest, callContext: ServerCallContext): Task?

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
     * - [ListTasksRequest.pageToken]: an opaque token from [ListTasksResponse.nextPageToken], in a storage-defined
     *   format. An invalid token must be reported with [A2AInvalidParamsException].
     * - [ListTasksRequest.pageSize]: `null` means the default of 50. The effective page size must be echoed in
     *   [ListTasksResponse.pageSize].
     * - [ListTasksRequest.historyLength] and [ListTasksRequest.includeArtifacts]: apply them to every returned task
     *   with [project]. `includeArtifacts = null` is treated as `false` (artifacts are omitted), `historyLength = null`
     *   returns the whole history, `0` returns `history = null`.
     * - The result may be scoped by [callContext], see [TaskStorage], including [ListTasksResponse.totalSize] and
     *   paging.
     *
     * Response requirements: [ListTasksResponse.totalSize] is the number of tasks matching the filters before
     * pagination, and [ListTasksResponse.nextPageToken] must be the empty string on the last page (the field is
     * always present on the wire).
     *
     * [ai.koog.a2a.server.A2AServer] validates [ListTasksRequest.pageSize] (`1..100`) and
     * [ListTasksRequest.historyLength] (not negative) beforehand; storages needn't repeat these checks.
     *
     * @param request the `ListTasks` request
     * @param callContext the context of the call
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
     * Deletes the task with [taskId]. Idempotent: deleting a task that doesn't exist is a no-op and never an error.
     *
     * The server never calls it; it is for the application, e.g. a retention job. An authorization-aware storage may
     * delete only within the scope of [callContext]; the in-memory implementation ignores it.
     *
     * @param taskId the task identifier
     * @param callContext the call the task is deleted for
     * @throws TaskOperationException if the task exists but can't be deleted
     */
    public suspend fun delete(taskId: String, callContext: ServerCallContext)
}
