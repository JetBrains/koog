package ai.koog.a2a.client

import ai.koog.a2a.exceptions.A2AException
import ai.koog.a2a.model.AgentCard
import ai.koog.a2a.model.CancelTaskRequest
import ai.koog.a2a.model.DeleteTaskPushNotificationConfigRequest
import ai.koog.a2a.model.Event
import ai.koog.a2a.model.GetExtendedAgentCardRequest
import ai.koog.a2a.model.GetTaskPushNotificationConfigRequest
import ai.koog.a2a.model.GetTaskRequest
import ai.koog.a2a.model.ListTaskPushNotificationConfigsRequest
import ai.koog.a2a.model.ListTaskPushNotificationConfigsResponse
import ai.koog.a2a.model.ListTasksRequest
import ai.koog.a2a.model.ListTasksResponse
import ai.koog.a2a.model.ResponseEvent
import ai.koog.a2a.model.SendMessageRequest
import ai.koog.a2a.model.SubscribeToTaskRequest
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskPushNotificationConfig
import ai.koog.a2a.transport.ClientCallContext
import ai.koog.a2a.transport.ClientTransport
import kotlinx.coroutines.flow.Flow
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * A2A client responsible for sending requests to A2A server.
 */
@OptIn(ExperimentalAtomicApi::class)
public open class A2AClient(
    private val transport: ClientTransport,
    private val agentCardResolver: AgentCardResolver,
) {
    protected var agentCard: AtomicReference<AgentCard?> = AtomicReference(null)

    /**
     * Performs initialization logic.
     * Currently only retrieves the [AgentCard].
     */
    public open suspend fun connect() {
        getAgentCard()
    }

    /**
     * Retrieves [AgentCard] by calling [AgentCardResolver.resolve].
     * Saves it to the cache.
     */
    public open suspend fun getAgentCard(): AgentCard {
        return agentCardResolver.resolve().also {
            agentCard.exchange(it)
        }
    }

    /**
     * Retrieves currently cached [AgentCard]
     *
     * @throws [IllegalStateException] if it's not initialized
     */
    public open fun cachedAgentCard(): AgentCard {
        return checkNotNull(agentCard.load()) { "Agent card is not initialized." }
    }

    /**
     * Calls [GetExtendedAgentCard](https://a2a-protocol.org/v1.0.1/specification/#3111-get-extended-agent-card).
     * Updates cached [AgentCard].
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun getExtendedAgentCard(
        request: GetExtendedAgentCardRequest,
        ctx: ClientCallContext = ClientCallContext.Default
    ): AgentCard {
        check(cachedAgentCard().capabilities.extendedAgentCard == true) {
            "Agent card reports that authenticated extended agent card is not supported."
        }

        return transport.getExtendedAgentCard(request, ctx).also {
            agentCard.exchange(it)
        }
    }

    /**
     * Calls [SendMessage](https://a2a-protocol.org/v1.0.1/specification/#311-send-message).
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun sendMessage(
        request: SendMessageRequest,
        ctx: ClientCallContext = ClientCallContext.Default
    ): ResponseEvent {
        return transport.sendMessage(request, ctx)
    }

    /**
     * Calls [SendStreamingMessage](https://a2a-protocol.org/v1.0.1/specification/#312-send-streaming-message)
     *
     * @throws A2AException if server returned an error.
     */
    public fun sendMessageStreaming(
        request: SendMessageRequest,
        ctx: ClientCallContext = ClientCallContext.Default
    ): Flow<Event> {
        check(cachedAgentCard().capabilities.streaming == true) {
            "Agent card reports that streaming is not supported."
        }

        return transport.sendMessageStreaming(request, ctx)
    }

    /**
     * Calls [GetTask](https://a2a-protocol.org/v1.0.1/specification/#313-get-task)
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun getTask(
        request: GetTaskRequest,
        ctx: ClientCallContext = ClientCallContext.Default
    ): Task {
        return transport.getTask(request, ctx)
    }

    /**
     * Calls [ListTasks](https://a2a-protocol.org/v1.0.1/specification/#314-list-tasks)
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun listTasks(
        request: ListTasksRequest,
        ctx: ClientCallContext = ClientCallContext.Default
    ): ListTasksResponse {
        return transport.listTasks(request, ctx)
    }

    /**
     * Calls [CancelTask](https://a2a-protocol.org/v1.0.1/specification/#315-cancel-task)
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun cancelTask(
        request: CancelTaskRequest,
        ctx: ClientCallContext = ClientCallContext.Default
    ): Task {
        return transport.cancelTask(request, ctx)
    }

    /**
     * Calls [SubscribeToTask](https://a2a-protocol.org/v1.0.1/specification/#316-subscribe-to-task)
     *
     * @throws A2AException if server returned an error.
     */
    public fun subscribeToTask(
        request: SubscribeToTaskRequest,
        ctx: ClientCallContext = ClientCallContext.Default
    ): Flow<Event> {
        return transport.subscribeToTask(request, ctx)
    }

    /**
     * Calls [CreateTaskPushNotificationConfig](https://a2a-protocol.org/v1.0.1/specification/#317-create-push-notification-config)
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun createTaskPushNotificationConfig(
        request: TaskPushNotificationConfig,
        ctx: ClientCallContext = ClientCallContext.Default
    ): TaskPushNotificationConfig {
        checkPushNotificationsSupported()

        return transport.createTaskPushNotificationConfig(request, ctx)
    }

    /**
     * Calls [GetTaskPushNotificationConfig](https://a2a-protocol.org/v1.0.1/specification/#318-get-push-notification-config)
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun getTaskPushNotificationConfig(
        request: GetTaskPushNotificationConfigRequest,
        ctx: ClientCallContext = ClientCallContext.Default
    ): TaskPushNotificationConfig {
        checkPushNotificationsSupported()

        return transport.getTaskPushNotificationConfig(request, ctx)
    }

    /**
     * Calls [ListTaskPushNotificationConfigs](https://a2a-protocol.org/v0.3.0/specification/#77-taskspushnotificationconfiglist)
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun listTaskPushNotificationConfigs(
        request: ListTaskPushNotificationConfigsRequest,
        ctx: ClientCallContext = ClientCallContext.Default
    ): ListTaskPushNotificationConfigsResponse {
        checkPushNotificationsSupported()

        return transport.listTaskPushNotificationConfigs(request, ctx)
    }

    /**
     * Calls [DeleteTaskPushNotificationConfig](https://a2a-protocol.org/v1.0.1/specification/#3110-delete-push-notification-config)
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun deleteTaskPushNotificationConfig(
        request: DeleteTaskPushNotificationConfigRequest,
        ctx: ClientCallContext = ClientCallContext.Default
    ) {
        checkPushNotificationsSupported()

        transport.deleteTaskPushNotificationConfig(request, ctx)
    }

    protected fun checkPushNotificationsSupported() {
        check(cachedAgentCard().capabilities.pushNotifications == true) {
            "Agent card reports that push notifications are not supported."
        }
    }
}
