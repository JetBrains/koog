package ai.koog.a2a.server.session

import ai.koog.a2a.server.messages.ContextMessageStorage
import ai.koog.a2a.server.tasks.ContextTaskStorage
import ai.koog.a2a.transport.ServerCallContext

/**
 * Request context associated with each A2A agent-related request, providing essential information and repositories to
 * the agent executor.
 *
 * @param contextId Context ID associated with this request.
 * @param callContext [ServerCallContext] associated with the request.
 * @param params Parameters associated with the request.
 * @param taskStorage [ContextTaskStorage] associated with the request.
 * @param messageStorage [ContextMessageStorage] associated with the request.
 */
public class RequestContext<T>(
    public val contextId: String,
    public val callContext: ServerCallContext,
    public val params: T,
    public val taskStorage: ContextTaskStorage,
    public val messageStorage: ContextMessageStorage,
)
