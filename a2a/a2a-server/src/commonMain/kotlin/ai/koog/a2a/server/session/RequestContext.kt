package ai.koog.a2a.server.session

import ai.koog.a2a.model.Task
import ai.koog.a2a.server.messages.ContextMessageStorage
import ai.koog.a2a.server.tasks.ContextTaskStorage
import ai.koog.a2a.transport.ServerCallContext

/**
 * Request context associated with each A2A agent-related request, providing essential information and repositories to
 * the agent executor.
 *
 * @property callContext [ServerCallContext] associated with the request.
 * @property params Parameters associated with the request.
 * @property taskStorage [ContextTaskStorage] associated with the request.
 * @property messageStorage [ContextMessageStorage] associated with the request.
 * @property contextId The context ID representing either an existing context from the incoming request in [params],
 * or a newly generated ID that the agent must use if it decides to reply.
 * @property taskId The task ID representing either an existing task from the incoming request in [params],
 * or a newly generated ID that the agent must use if it decides to create a new task.
 * @property task Optional shallow version of the current task (without message history or artifacts)
 * providing lightweight access to general task state information. Present only if the incoming request
 * was associated with an existing task. For detailed task information or referenced tasks,
 * query [taskStorage] directly.
 */
public class RequestContext<T>(
    public val callContext: ServerCallContext,
    public val params: T,
    public val taskStorage: ContextTaskStorage,
    public val messageStorage: ContextMessageStorage,
    public val contextId: String,
    public val taskId: String,
    public val task: Task?,
)
