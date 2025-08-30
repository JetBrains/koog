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
 * Server transport implementing server handling of
 * [A2A protocol methods](https://a2a-protocol.org/latest/specification/#7-protocol-rpc-methods).
 */
public interface ServerTransport {
    /**
     * Handles [agent/getAuthenticatedExtendedCard](https://a2a-protocol.org/latest/specification/#710-agentgetauthenticatedextendedcard)
     *
     * @throws A2AException if there is an error with processsing the request.
     */
    public val onGetAuthenticatedExtendedAgentCard: suspend (
        request: Request<Unit>,
        ctx: ServerCallContext
    ) -> Response<AgentCard>

    /**
     * Handles [message/send](https://a2a-protocol.org/latest/specification/#71-messagesend).
     *
     * @throws A2AException if there is an error with processsing the request.
     */
    public val onSendMessage: suspend (
        request: Request<MessageSendParams>,
        ctx: ServerCallContext
    ) -> Response<Communication>

    /**
     * Handles [message/stream](https://a2a-protocol.org/latest/specification/#72-messagestream)
     *
     * @throws A2AException if there is an error with processsing the request.
     */
    public val onSendMessageStreaming: suspend (
        request: Request<MessageSendParams>,
        ctx: ServerCallContext
    ) -> Flow<Response<Event>>

    /**
     * Handles [tasks/get](https://a2a-protocol.org/latest/specification/#73-tasksget)
     *
     * @throws A2AException if there is an error with processsing the request.
     */
    public val onGetTask: suspend (
        request: Request<TaskQueryParams>,
        ctx: ServerCallContext
    ) -> Response<Task>

    /**
     * Handles [tasks/cancel](https://a2a-protocol.org/latest/specification/#74-taskscancel)
     *
     * @throws A2AException if there is an error with processsing the request.
     */
    public val onCancelTask: suspend (
        request: Request<TaskIdParams>,
        ctx: ServerCallContext
    ) -> Response<Task>

    /**
     * Handles [tasks/pushNotificationConfig/set](https://a2a-protocol.org/latest/specification/#75-taskspushnotificationconfigset)
     *
     * @throws A2AException if there is an error with processsing the request.
     */
    public val onSetTaskPushNotificationConfig: suspend (
        request: Request<TaskPushNotificationConfig>,
        ctx: ServerCallContext
    ) -> Response<TaskPushNotificationConfig>

    /**
     * Handles [tasks/pushNotificationConfig/get](https://a2a-protocol.org/latest/specification/#76-taskspushnotificationconfigget)
     *
     * @throws A2AException if there is an error with processsing the request.
     */
    public val onGetTaskPushNotificationConfig: suspend (
        request: Request<TaskPushNotificationConfigParams>,
        ctx: ServerCallContext
    ) -> Response<TaskPushNotificationConfig>

    /**
     * Handles [tasks/pushNotificationConfig/list](https://a2a-protocol.org/latest/specification/#77-taskspushnotificationconfiglist)
     *
     * @throws A2AException if there is an error with processsing the request.
     */
    public val onListTaskPushNotificationConfig: suspend (
        request: Request<TaskIdParams>,
        ctx: ServerCallContext
    ) -> Response<List<TaskPushNotificationConfig>>

    /**
     * Handles [tasks/pushNotificationConfig/delete](https://a2a-protocol.org/latest/specification/#78-taskspushnotificationconfigdelete)
     *
     * @throws A2AException if there is an error with processsing the request.
     */
    public val onDeleteTaskPushNotificationConfig: suspend (
        request: Request<TaskPushNotificationConfigParams>,
        ctx: ServerCallContext
    ) -> Response<Unit>
}

/**
 * Represents the server context of a call.
 *
 * @property headers Headers associated with the call.
 */
public class ServerCallContext(
    public val headers: Map<String, String> = emptyMap(),
) {
    @Suppress("MissingKDocForPublicAPI")
    public companion object {
        public val Default: ServerCallContext = ServerCallContext()
    }
}
