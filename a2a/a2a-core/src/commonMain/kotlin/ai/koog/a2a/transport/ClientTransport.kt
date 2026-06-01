package ai.koog.a2a.transport

import ai.koog.a2a.exceptions.A2AException
import ai.koog.a2a.model.AgentCard
import ai.koog.a2a.model.Event
import ai.koog.a2a.model.MessageSendParams
import ai.koog.a2a.model.ResponseEvent
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskIdParams
import ai.koog.a2a.model.TaskPushNotificationConfig
import ai.koog.a2a.model.TaskPushNotificationConfigParams
import ai.koog.a2a.model.TaskQueryParams
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerializationException

/**
 * Client transport making requests to [A2A protocol methods](https://a2a-protocol.org/v0.3.0/specification/#7-protocol-rpc-methods)
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
     * Calls [agent/getAuthenticatedExtendedCard](https://a2a-protocol.org/v0.3.0/specification/#710-agentgetauthenticatedextendedcard)
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun getAuthenticatedExtendedAgentCard(
        ctx: ClientCallContext = ClientCallContext.Default
    ): AgentCard

    /**
     * Calls [message/send](https://a2a-protocol.org/v0.3.0/specification/#71-messagesend).
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun sendMessage(
        request: MessageSendParams,
        ctx: ClientCallContext = ClientCallContext.Default
    ): ResponseEvent

    /**
     * Calls [message/stream](https://a2a-protocol.org/v0.3.0/specification/#72-messagestream)
     *
     * @throws A2AException if server returned an error.
     */
    public fun sendMessageStreaming(
        request: MessageSendParams,
        ctx: ClientCallContext = ClientCallContext.Default
    ): Flow<Event>

    /**
     * Calls [tasks/get](https://a2a-protocol.org/v0.3.0/specification/#73-tasksget)
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun getTask(
        request: TaskQueryParams,
        ctx: ClientCallContext = ClientCallContext.Default
    ): Task

    /**
     * Calls [tasks/cancel](https://a2a-protocol.org/v0.3.0/specification/#74-taskscancel)
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun cancelTask(
        request: TaskIdParams,
        ctx: ClientCallContext = ClientCallContext.Default
    ): Task

    /**
     * Calls [tasks/resubscribe](https://a2a-protocol.org/v0.3.0/specification/#79-tasksresubscribe)
     *
     * @throws A2AException if server returned an error.
     */
    public fun resubscribeTask(
        request: TaskIdParams,
        ctx: ClientCallContext = ClientCallContext.Default
    ): Flow<Event>

    /**
     * Calls [tasks/pushNotificationConfig/set](https://a2a-protocol.org/v0.3.0/specification/#75-taskspushnotificationconfigset)
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun setTaskPushNotificationConfig(
        request: TaskPushNotificationConfig,
        ctx: ClientCallContext = ClientCallContext.Default
    ): TaskPushNotificationConfig

    /**
     * Calls [tasks/pushNotificationConfig/get](https://a2a-protocol.org/v0.3.0/specification/#76-taskspushnotificationconfigget)
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun getTaskPushNotificationConfig(
        request: TaskPushNotificationConfigParams,
        ctx: ClientCallContext = ClientCallContext.Default
    ): TaskPushNotificationConfig

    /**
     * Calls [tasks/pushNotificationConfig/list](https://a2a-protocol.org/v0.3.0/specification/#77-taskspushnotificationconfiglist)
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun listTaskPushNotificationConfig(
        request: TaskIdParams,
        ctx: ClientCallContext = ClientCallContext.Default
    ): List<TaskPushNotificationConfig>

    /**
     * Calls [tasks/pushNotificationConfig/delete](https://a2a-protocol.org/v0.3.0/specification/#78-taskspushnotificationconfigdelete)
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun deleteTaskPushNotificationConfig(
        request: TaskPushNotificationConfigParams,
        ctx: ClientCallContext = ClientCallContext.Default
    )
}

/**
 * Represents the client context of a call.
 *
 * @property headers Additional call-specific headers associated with the call.
 */
public class ClientCallContext(
    public val headers: Map<String, List<String>> = emptyMap(),
) {
    @Suppress("MissingKDocForPublicAPI")
    public companion object {
        public val Default: ClientCallContext = ClientCallContext()
    }
}
