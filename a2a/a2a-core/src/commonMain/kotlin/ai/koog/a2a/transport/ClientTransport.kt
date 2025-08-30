package ai.koog.a2a.transport

import ai.koog.a2a.exceptions.A2AException
import ai.koog.a2a.model.AgentCard
import ai.koog.a2a.model.Communication
import ai.koog.a2a.model.Event
import ai.koog.a2a.model.MessageSendParams
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskIdParams
import ai.koog.a2a.model.TaskPushNotificationConfig
import ai.koog.a2a.model.TaskPushNotificationConfigParams
import ai.koog.a2a.model.TaskQueryParams
import kotlinx.coroutines.flow.Flow

/**
 * Client transport implementing client interactions with
 * [A2A protocol methods](https://a2a-protocol.org/latest/specification/#7-protocol-rpc-methods).
 */
public interface ClientTransport {
    /**
     * Retrieves the agent card.
     */
    public suspend fun getAgentCard(): AgentCard

    /**
     * Implements [agent/getAuthenticatedExtendedCard](https://a2a-protocol.org/latest/specification/#710-agentgetauthenticatedextendedcard)
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun getAuthenticatedExtendedAgentCard(
        request: Request<Unit>,
        ctx: CallContext = CallContext.Default
    ): Response<AgentCard>

    /**
     * Implements [message/send](https://a2a-protocol.org/latest/specification/#71-messagesend).
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun sendMessage(
        request: Request<MessageSendParams>,
        ctx: CallContext = CallContext.Default
    ): Response<Communication>

    /**
     * Implements [message/stream](https://a2a-protocol.org/latest/specification/#72-messagestream)
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun sendMessageStreaming(
        request: Request<MessageSendParams>,
        ctx: CallContext = CallContext.Default
    ): Flow<Response<Event>>

    /**
     * Implements [tasks/get](https://a2a-protocol.org/latest/specification/#73-tasksget)
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun getTask(
        request: Request<TaskQueryParams>,
        ctx: CallContext = CallContext.Default
    ): Response<Task>

    /**
     * Implements [tasks/cancel](https://a2a-protocol.org/latest/specification/#74-taskscancel)
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun cancelTask(
        request: Request<TaskIdParams>,
        ctx: CallContext = CallContext.Default
    ): Response<Task>

    /**
     * Implements [tasks/pushNotificationConfig/set](https://a2a-protocol.org/latest/specification/#75-taskspushnotificationconfigset)
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun setTaskPushNotificationConfig(
        request: Request<TaskPushNotificationConfig>,
        ctx: CallContext = CallContext.Default
    ): Response<TaskPushNotificationConfig>

    /**
     * Implements [tasks/pushNotificationConfig/get](https://a2a-protocol.org/latest/specification/#76-taskspushnotificationconfigget)
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun getTaskPushNotificationConfig(
        request: Request<TaskPushNotificationConfigParams>,
        ctx: CallContext = CallContext.Default
    ): Response<TaskPushNotificationConfig>

    /**
     * Implements [tasks/pushNotificationConfig/list](https://a2a-protocol.org/latest/specification/#77-taskspushnotificationconfiglist)
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun listTaskPushNotificationConfig(
        request: Request<TaskIdParams>,
        ctx: CallContext = CallContext.Default
    ): Response<List<TaskPushNotificationConfig>>

    /**
     * Implements [tasks/pushNotificationConfig/delete](https://a2a-protocol.org/latest/specification/#78-taskspushnotificationconfigdelete)
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun deleteTaskPushNotificationConfig(
        request: Request<TaskPushNotificationConfigParams>,
        ctx: CallContext = CallContext.Default
    ): Response<Unit>
}
