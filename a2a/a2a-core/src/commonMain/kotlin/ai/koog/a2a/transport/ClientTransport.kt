package ai.koog.a2a.transport

import ai.koog.a2a.exceptions.A2AException
import ai.koog.a2a.model.AgentCard
import ai.koog.a2a.model.CommunicationEvent
import ai.koog.a2a.model.Event
import ai.koog.a2a.model.MessageSendParams
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskIdParams
import ai.koog.a2a.model.TaskPushNotificationConfig
import ai.koog.a2a.model.TaskPushNotificationConfigParams
import ai.koog.a2a.model.TaskQueryParams
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

/**
 * Client transport making requests to [A2A protocol methods](https://a2a-protocol.org/latest/specification/#7-protocol-rpc-methods)
 * and handling responses from the server.
 *
 * Client transport must handle error responses from the server and convert them to appropriate [A2AException]
 * (e.g. parsing error response data format like JSON error object and throwing corresponding [A2AException] based on the error code).
 * It must preserve the [A2AException.errorCode] received from the [ServerTransport].
 *
 * Client transport may throw exceptions other than [A2AException] for any transport-level errors (e.g. network failures, invalid responses, timeout),
 * e.g. [SerializationException]
 */
public interface ClientTransport : AutoCloseable {
    /**
     * Calls [agent/getAuthenticatedExtendedCard](https://a2a-protocol.org/latest/specification/#710-agentgetauthenticatedextendedcard)
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun getAuthenticatedExtendedAgentCard(
        request: Request<Nothing?>,
        ctx: ClientCallContext = ClientCallContext.Default
    ): Response<AgentCard>

    /**
     * Calls [message/send](https://a2a-protocol.org/latest/specification/#71-messagesend).
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun sendMessage(
        request: Request<MessageSendParams>,
        ctx: ClientCallContext = ClientCallContext.Default
    ): Response<CommunicationEvent>

    /**
     * Calls [message/stream](https://a2a-protocol.org/latest/specification/#72-messagestream)
     *
     * @throws A2AException if server returned an error.
     */
    public fun sendMessageStreaming(
        request: Request<MessageSendParams>,
        ctx: ClientCallContext = ClientCallContext.Default
    ): Flow<Response<Event>>

    /**
     * Calls [tasks/get](https://a2a-protocol.org/latest/specification/#73-tasksget)
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun getTask(
        request: Request<TaskQueryParams>,
        ctx: ClientCallContext = ClientCallContext.Default
    ): Response<Task>

    /**
     * Calls [tasks/cancel](https://a2a-protocol.org/latest/specification/#74-taskscancel)
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun cancelTask(
        request: Request<TaskIdParams>,
        ctx: ClientCallContext = ClientCallContext.Default
    ): Response<Task>

    /**
     * Calls [tasks/resubscribe](https://a2a-protocol.org/latest/specification/#79-tasksresubscribe)
     *
     * @throws A2AException if server returned an error.
     */
    public fun resubscribeTask(
        request: Request<TaskIdParams>,
        ctx: ClientCallContext = ClientCallContext.Default
    ): Flow<Response<Event>>

    /**
     * Calls [tasks/pushNotificationConfig/set](https://a2a-protocol.org/latest/specification/#75-taskspushnotificationconfigset)
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun setTaskPushNotificationConfig(
        request: Request<TaskPushNotificationConfig>,
        ctx: ClientCallContext = ClientCallContext.Default
    ): Response<TaskPushNotificationConfig>

    /**
     * Calls [tasks/pushNotificationConfig/get](https://a2a-protocol.org/latest/specification/#76-taskspushnotificationconfigget)
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun getTaskPushNotificationConfig(
        request: Request<TaskPushNotificationConfigParams>,
        ctx: ClientCallContext = ClientCallContext.Default
    ): Response<TaskPushNotificationConfig>

    /**
     * Calls [tasks/pushNotificationConfig/list](https://a2a-protocol.org/latest/specification/#77-taskspushnotificationconfiglist)
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun listTaskPushNotificationConfig(
        request: Request<TaskIdParams>,
        ctx: ClientCallContext = ClientCallContext.Default
    ): Response<List<TaskPushNotificationConfig>>

    /**
     * Calls [tasks/pushNotificationConfig/delete](https://a2a-protocol.org/latest/specification/#78-taskspushnotificationconfigdelete)
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun deleteTaskPushNotificationConfig(
        request: Request<TaskPushNotificationConfigParams>,
        ctx: ClientCallContext = ClientCallContext.Default
    ): Response<Nothing?>
}

/**
 * Represents the client context of a call.
 *
 * @property additionalHeaders Additional call-specific headers associated with the call.
 */
@Serializable
public data class ClientCallContext(
    public val additionalHeaders: Map<String, List<String>> = emptyMap(),
) {
    @Suppress("MissingKDocForPublicAPI")
    public companion object {
        public val Default: ClientCallContext = ClientCallContext()
    }
}
