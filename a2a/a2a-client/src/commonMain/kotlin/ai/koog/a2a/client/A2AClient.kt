package ai.koog.a2a.client

import ai.koog.a2a.exceptions.A2AException
import ai.koog.a2a.model.AgentCard
import ai.koog.a2a.model.CommunicationEvent
import ai.koog.a2a.model.MessageSendParams
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskIdParams
import ai.koog.a2a.model.TaskPushNotificationConfig
import ai.koog.a2a.model.TaskPushNotificationConfigParams
import ai.koog.a2a.model.TaskQueryParams
import ai.koog.a2a.model.UpdateEvent
import ai.koog.a2a.transport.ClientCallContext
import ai.koog.a2a.transport.ClientTransport
import ai.koog.a2a.transport.Request
import ai.koog.a2a.transport.Response
import kotlinx.coroutines.flow.Flow
import kotlin.concurrent.Volatile

/**
 * A2A client responsible for sending requests to A2A server.
 */
public open class A2AClient(
    private val transport: ClientTransport,
    private val agentCardResolver: AgentCardResolver,
) {
    /**
     * Currently cached version of the agent card.
     * Shouldn't be used directly to read values from it, since it can be updated by the [loadAgentCard] method.
     * Always use [getAgentCard] instead.
     */
    @Volatile
    protected open lateinit var agentCard: AgentCard

    /**
     * Resolve agent card from the provided [agentCardResolver] and cache it.
     * Can be called multiple times, to update cached version of the agent card.
     */
    public open suspend fun loadAgentCard(): AgentCard {
        return agentCardResolver.resolve().also {
            agentCard = it
        }
    }

    /**
     * Get current cached version of agent card.
     */
    public open fun getAgentCard(): AgentCard = agentCard

    /**
     * Calls [agent/getAuthenticatedExtendedCard](https://a2a-protocol.org/latest/specification/#710-agentgetauthenticatedextendedcard)
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun getAuthenticatedExtendedAgentCard(
        request: Request<Nothing?>,
        ctx: ClientCallContext = ClientCallContext.Default
    ): Response<AgentCard> {
        check(getAgentCard().supportsAuthenticatedExtendedCard == true) {
            "Agent card reports that authenticated extended agent card is not supported."
        }

        return transport.getAuthenticatedExtendedAgentCard(request, ctx).also {
            agentCard = it.data
        }
    }

    /**
     * Calls [message/send](https://a2a-protocol.org/latest/specification/#71-messagesend).
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun sendMessage(
        request: Request<MessageSendParams>,
        ctx: ClientCallContext = ClientCallContext.Default
    ): Response<CommunicationEvent> {
        return transport.sendMessage(request, ctx)
    }

    /**
     * Calls [message/stream](https://a2a-protocol.org/latest/specification/#72-messagestream)
     *
     * @throws A2AException if server returned an error.
     */
    public fun sendMessageStreaming(
        request: Request<MessageSendParams>,
        ctx: ClientCallContext = ClientCallContext.Default
    ): Flow<Response<UpdateEvent>> {
        check(getAgentCard().capabilities.streaming == true) {
            "Agent card reports that streaming is not supported."
        }

        return transport.sendMessageStreaming(request, ctx)
    }

    /**
     * Calls [tasks/get](https://a2a-protocol.org/latest/specification/#73-tasksget)
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun getTask(
        request: Request<TaskQueryParams>,
        ctx: ClientCallContext = ClientCallContext.Default
    ): Response<Task> {
        return transport.getTask(request, ctx)
    }

    /**
     * Calls [tasks/cancel](https://a2a-protocol.org/latest/specification/#74-taskscancel)
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun cancelTask(
        request: Request<TaskIdParams>,
        ctx: ClientCallContext = ClientCallContext.Default
    ): Response<Task> {
        return transport.cancelTask(request, ctx)
    }

    /**
     * Calls [tasks/resubscribe](https://a2a-protocol.org/latest/specification/#79-tasksresubscribe)
     *
     * @throws A2AException if server returned an error.
     */
    public fun resubscribeTask(
        request: Request<TaskIdParams>,
        ctx: ClientCallContext = ClientCallContext.Default
    ): Flow<Response<UpdateEvent>> {
        return transport.resubscribeTask(request, ctx)
    }

    /**
     * Calls [tasks/pushNotificationConfig/set](https://a2a-protocol.org/latest/specification/#75-taskspushnotificationconfigset)
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun setTaskPushNotificationConfig(
        request: Request<TaskPushNotificationConfig>,
        ctx: ClientCallContext = ClientCallContext.Default
    ): Response<TaskPushNotificationConfig> {
        checkPushNotificationsSupported()

        return transport.setTaskPushNotificationConfig(request, ctx)
    }

    /**
     * Calls [tasks/pushNotificationConfig/get](https://a2a-protocol.org/latest/specification/#76-taskspushnotificationconfigget)
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun getTaskPushNotificationConfig(
        request: Request<TaskPushNotificationConfigParams>,
        ctx: ClientCallContext = ClientCallContext.Default
    ): Response<TaskPushNotificationConfig> {
        checkPushNotificationsSupported()

        return transport.getTaskPushNotificationConfig(request, ctx)
    }

    /**
     * Calls [tasks/pushNotificationConfig/list](https://a2a-protocol.org/latest/specification/#77-taskspushnotificationconfiglist)
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun listTaskPushNotificationConfig(
        request: Request<TaskIdParams>,
        ctx: ClientCallContext = ClientCallContext.Default
    ): Response<List<TaskPushNotificationConfig>> {
        checkPushNotificationsSupported()

        return transport.listTaskPushNotificationConfig(request, ctx)
    }

    /**
     * Calls [tasks/pushNotificationConfig/delete](https://a2a-protocol.org/latest/specification/#78-taskspushnotificationconfigdelete)
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun deleteTaskPushNotificationConfig(
        request: Request<TaskPushNotificationConfigParams>,
        ctx: ClientCallContext = ClientCallContext.Default
    ): Response<Nothing?> {
        checkPushNotificationsSupported()

        return transport.deleteTaskPushNotificationConfig(request, ctx)
    }

    private fun checkPushNotificationsSupported() {
        check(getAgentCard().capabilities.pushNotifications == true) {
            "Agent card reports that push notifications are not supported."
        }
    }
}
