package ai.koog.a2a.transport

import ai.koog.a2a.exceptions.A2AException
import ai.koog.a2a.exceptions.A2AInternalErrorException
import ai.koog.a2a.model.AgentCard
import ai.koog.a2a.model.CommunicationEvent
import ai.koog.a2a.model.MessageSendParams
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskIdParams
import ai.koog.a2a.model.TaskPushNotificationConfig
import ai.koog.a2a.model.TaskPushNotificationConfigParams
import ai.koog.a2a.model.TaskQueryParams
import ai.koog.a2a.model.UpdateEvent
import kotlinx.coroutines.flow.Flow

/**
 * Server transport processing raw requests made to [A2A protocol methods](https://a2a-protocol.org/latest/specification/#7-protocol-rpc-methods)
 * and delegating the processing to [RequestHandler].
 *
 * Server transport must respond with appropriate [A2AException] in case of errors while processing the request
 * (e.g. method not found or invalid method parameters). It must also handle [A2AException] thrown by the [RequestHandler] methods.
 * In case non [A2AException] is thrown, it must be converted to [A2AInternalErrorException] with appropriate message.
 *
 * Server transport must convert [A2AException] to appropriate response data format (e.g. JSON error object),
 * preserving the [A2AException.errorCode] so that it can be properly handled by the [ClientTransport].
 */
public interface ServerTransport {
    /**
     * Handler responsible for processing parsed A2A requests.
     */
    public val requestHandler: RequestHandler
}

/**
 * Handler responsible for processing parsed A2A requests, implementing
 * [A2A protocol methods](https://a2a-protocol.org/latest/specification/#7-protocol-rpc-methods).
 */
public interface RequestHandler {
    /**
     * Handles [agent/getAuthenticatedExtendedCard](https://a2a-protocol.org/latest/specification/#710-agentgetauthenticatedextendedcard)
     *
     * @throws A2AException if there is an error with processsing the request.
     */
    public suspend fun onGetAuthenticatedExtendedAgentCard(
        request: Request<Nothing?>,
        ctx: ServerCallContext
    ): Response<AgentCard>

    /**
     * Handles [message/send](https://a2a-protocol.org/latest/specification/#71-messagesend).
     *
     * @throws A2AException if there is an error with processsing the request.
     */
    public suspend fun onSendMessage(
        request: Request<MessageSendParams>,
        ctx: ServerCallContext
    ): Response<CommunicationEvent>

    /**
     * Handles [message/stream](https://a2a-protocol.org/latest/specification/#72-messagestream)
     *
     * @throws A2AException if there is an error with processsing the request.
     */
    public fun onSendMessageStreaming(
        request: Request<MessageSendParams>,
        ctx: ServerCallContext
    ): Flow<Response<UpdateEvent>>

    /**
     * Handles [tasks/get](https://a2a-protocol.org/latest/specification/#73-tasksget)
     *
     * @throws A2AException if there is an error with processsing the request.
     */
    public suspend fun onGetTask(
        request: Request<TaskQueryParams>,
        ctx: ServerCallContext
    ): Response<Task>

    /**
     * Handles [tasks/resubscribe](https://a2a-protocol.org/latest/specification/#79-tasksresubscribe)
     *
     * @throws A2AException if there is an error with processsing the request.
     */
    public fun onResubscribeTask(
        request: Request<TaskIdParams>,
        ctx: ServerCallContext
    ): Flow<Response<UpdateEvent>>

    /**
     * Handles [tasks/cancel](https://a2a-protocol.org/latest/specification/#74-taskscancel)
     *
     * @throws A2AException if there is an error with processsing the request.
     */
    public suspend fun onCancelTask(
        request: Request<TaskIdParams>,
        ctx: ServerCallContext
    ): Response<Task>

    /**
     * Handles [tasks/pushNotificationConfig/set](https://a2a-protocol.org/latest/specification/#75-taskspushnotificationconfigset)
     *
     * @throws A2AException if there is an error with processsing the request.
     */
    public suspend fun onSetTaskPushNotificationConfig(
        request: Request<TaskPushNotificationConfig>,
        ctx: ServerCallContext
    ): Response<TaskPushNotificationConfig>

    /**
     * Handles [tasks/pushNotificationConfig/get](https://a2a-protocol.org/latest/specification/#76-taskspushnotificationconfigget)
     *
     * @throws A2AException if there is an error with processsing the request.
     */
    public suspend fun onGetTaskPushNotificationConfig(
        request: Request<TaskPushNotificationConfigParams>,
        ctx: ServerCallContext
    ): Response<TaskPushNotificationConfig>

    /**
     * Handles [tasks/pushNotificationConfig/list](https://a2a-protocol.org/latest/specification/#77-taskspushnotificationconfiglist)
     *
     * @throws A2AException if there is an error with processsing the request.
     */
    public suspend fun onListTaskPushNotificationConfig(
        request: Request<TaskIdParams>,
        ctx: ServerCallContext
    ): Response<List<TaskPushNotificationConfig>>

    /**
     * Handles [tasks/pushNotificationConfig/delete](https://a2a-protocol.org/latest/specification/#78-taskspushnotificationconfigdelete)
     *
     * @throws A2AException if there is an error with processsing the request.
     */
    public suspend fun onDeleteTaskPushNotificationConfig(
        request: Request<TaskPushNotificationConfigParams>,
        ctx: ServerCallContext
    ): Response<Nothing?>
}

/**
 * Represents the server context of a call.
 *
 * @property headers Headers associated with the call.
 */
public class ServerCallContext(
    public val headers: Map<String, List<String>> = emptyMap(),
) {
    @Suppress("MissingKDocForPublicAPI")
    public companion object {
        public val Default: ServerCallContext = ServerCallContext()
    }
}
