package ai.koog.a2a.server.turn

import ai.koog.a2a.model.Task
import ai.koog.a2a.server.messages.ContextMessageStorage
import ai.koog.a2a.transport.ServerCallContext

/**
 * Request context associated with each A2A agent-related request, providing essential information and repositories to
 * the agent executor.
 *
 * @property callContext [ServerCallContext] associated with the request.
 * @property params Parameters associated with the request.
 * @property messageStorage [ContextMessageStorage] associated with the request.
 * @property contextId The context ID representing either an existing context from the incoming request in [params],
 * or a newly generated ID that the agent must use if it decides to reply.
 * @property taskId The task ID representing either an existing task from the incoming request in [params],
 * or a newly generated ID that the agent must use if it decides to create a new task.
 * @property task The task as stored when the request is handled, with all [Task.artifacts]. Present only if the
 * incoming request was associated with an existing task. For SendMessage, it has the full [Task.history] as of the turn
 * start and does not contain the user message of the request (available in [params]): the server adds it to the
 * history lazily, when the agent sends its first status or artifact update. For CancelTask, it has no history.
 * @property referencedTasks For SendMessage, the tasks of the message's
 * [referenceTaskIds][ai.koog.a2a.model.Message.referenceTaskIds], read before the turn starts if the server loads them
 * (see [ai.koog.a2a.server.A2AServer]'s `loadReferencedTasks`): in the order of the references, without duplicates,
 * with full history and artifacts. Tasks that don't exist or aren't visible to [callContext] are left out. `null` if
 * the tasks were not loaded, and always for CancelTask.
 */
public class RequestContext<T>(
    public val callContext: ServerCallContext,
    public val params: T,
    public val messageStorage: ContextMessageStorage,
    public val contextId: String,
    public val taskId: String,
    public val task: Task?,
    public val referencedTasks: List<Task>?,
)
