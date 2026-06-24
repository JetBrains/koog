package ai.koog.a2a.client

import ai.koog.a2a.consts.A2AHeaders
import ai.koog.a2a.consts.A2AVersions
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
 *
 * @param card The initial [AgentCard].
 * @param transport The transport implementation to be used for sending requests.
 */
@OptIn(ExperimentalAtomicApi::class)
public open class A2AClient(
    card: AgentCard,
    private val transport: ClientTransport,
) : AutoCloseable {
    /**
     * Backing field containing the actual cached [AgentCard]
     */
    @Suppress("PropertyName")
    protected open val _card: AtomicReference<AgentCard> = AtomicReference(card)

    /**
     * The currently cached [AgentCard].
     * Initially set to the card provided during initialization.
     * Updated by calls to [getExtendedAgentCard].
     */
    public val card: AgentCard get() = _card.load()

    /**
     * Calls [GetExtendedAgentCard](https://a2a-protocol.org/v1.0.1/specification/#3111-get-extended-agent-card).
     * Updates cached [AgentCard].
     *
     * @throws A2AException if server returned an error.
     */
    public open suspend fun getExtendedAgentCard(
        request: GetExtendedAgentCardRequest,
        ctx: ClientCallContext = ClientCallContext.Default
    ): AgentCard {
        checkExtendedAgentCardSupported()

        return transport.getExtendedAgentCard(request, prepareContext(ctx)).also {
            _card.exchange(it)
        }
    }

    /**
     * Calls [SendMessage](https://a2a-protocol.org/v1.0.1/specification/#311-send-message).
     *
     * @throws A2AException if server returned an error.
     */
    public open suspend fun sendMessage(
        request: SendMessageRequest,
        ctx: ClientCallContext = ClientCallContext.Default
    ): ResponseEvent {
        return transport.sendMessage(request, prepareContext(ctx))
    }

    /**
     * Calls [SendStreamingMessage](https://a2a-protocol.org/v1.0.1/specification/#312-send-streaming-message)
     *
     * @throws A2AException if server returned an error.
     */
    public open fun sendMessageStreaming(
        request: SendMessageRequest,
        ctx: ClientCallContext = ClientCallContext.Default
    ): Flow<Event> {
        checkStreamingSupported()

        return transport.sendMessageStreaming(request, prepareContext(ctx))
    }

    /**
     * Calls [GetTask](https://a2a-protocol.org/v1.0.1/specification/#313-get-task)
     *
     * @throws A2AException if server returned an error.
     */
    public open suspend fun getTask(
        request: GetTaskRequest,
        ctx: ClientCallContext = ClientCallContext.Default
    ): Task {
        return transport.getTask(request, prepareContext(ctx))
    }

    /**
     * Calls [ListTasks](https://a2a-protocol.org/v1.0.1/specification/#314-list-tasks)
     *
     * @throws A2AException if server returned an error.
     */
    public open suspend fun listTasks(
        request: ListTasksRequest,
        ctx: ClientCallContext = ClientCallContext.Default
    ): ListTasksResponse {
        return transport.listTasks(request, prepareContext(ctx))
    }

    /**
     * Calls [CancelTask](https://a2a-protocol.org/v1.0.1/specification/#315-cancel-task)
     *
     * @throws A2AException if server returned an error.
     */
    public open suspend fun cancelTask(
        request: CancelTaskRequest,
        ctx: ClientCallContext = ClientCallContext.Default
    ): Task {
        return transport.cancelTask(request, prepareContext(ctx))
    }

    /**
     * Calls [SubscribeToTask](https://a2a-protocol.org/v1.0.1/specification/#316-subscribe-to-task)
     *
     * @throws A2AException if server returned an error.
     */
    public open fun subscribeToTask(
        request: SubscribeToTaskRequest,
        ctx: ClientCallContext = ClientCallContext.Default
    ): Flow<Event> {
        return transport.subscribeToTask(request, prepareContext(ctx))
    }

    /**
     * Calls [CreateTaskPushNotificationConfig](https://a2a-protocol.org/v1.0.1/specification/#317-create-push-notification-config)
     *
     * @throws A2AException if server returned an error.
     */
    public open suspend fun createTaskPushNotificationConfig(
        request: TaskPushNotificationConfig,
        ctx: ClientCallContext = ClientCallContext.Default
    ): TaskPushNotificationConfig {
        checkPushNotificationsSupported()

        return transport.createTaskPushNotificationConfig(request, prepareContext(ctx))
    }

    /**
     * Calls [GetTaskPushNotificationConfig](https://a2a-protocol.org/v1.0.1/specification/#318-get-push-notification-config)
     *
     * @throws A2AException if server returned an error.
     */
    public open suspend fun getTaskPushNotificationConfig(
        request: GetTaskPushNotificationConfigRequest,
        ctx: ClientCallContext = ClientCallContext.Default
    ): TaskPushNotificationConfig {
        checkPushNotificationsSupported()

        return transport.getTaskPushNotificationConfig(request, prepareContext(ctx))
    }

    /**
     * Calls [ListTaskPushNotificationConfigs](https://a2a-protocol.org/v0.3.0/specification/#77-taskspushnotificationconfiglist)
     *
     * @throws A2AException if server returned an error.
     */
    public open suspend fun listTaskPushNotificationConfigs(
        request: ListTaskPushNotificationConfigsRequest,
        ctx: ClientCallContext = ClientCallContext.Default
    ): ListTaskPushNotificationConfigsResponse {
        checkPushNotificationsSupported()

        return transport.listTaskPushNotificationConfigs(request, prepareContext(ctx))
    }

    /**
     * Calls [DeleteTaskPushNotificationConfig](https://a2a-protocol.org/v1.0.1/specification/#3110-delete-push-notification-config)
     *
     * @throws A2AException if server returned an error.
     */
    public open suspend fun deleteTaskPushNotificationConfig(
        request: DeleteTaskPushNotificationConfigRequest,
        ctx: ClientCallContext = ClientCallContext.Default
    ) {
        checkPushNotificationsSupported()

        transport.deleteTaskPushNotificationConfig(request, prepareContext(ctx))
    }

    override fun close() {
        transport.close()
    }

    /**
     * Updates [ClientCallContext] with additional info before each request, e.g., version header.
     */
    protected open fun prepareContext(ctx: ClientCallContext): ClientCallContext {
        val updatedHeaders = ctx.headers.toMutableMap()

        // Append current version header, if the version header is missing
        if (A2AHeaders.A2A_VERSION !in ctx.headers) {
            updatedHeaders += (A2AHeaders.A2A_VERSION to listOf(A2AVersions.CURRENT_VERSION))
        }

        return ctx.copy(
            headers = updatedHeaders
        )
    }

    protected fun checkExtendedAgentCardSupported() {
        check(card.capabilities.extendedAgentCard == true) {
            "Agent card reports that authenticated extended agent card is not supported."
        }
    }

    protected fun checkStreamingSupported() {
        check(card.capabilities.streaming == true) {
            "Agent card reports that streaming is not supported."
        }
    }

    protected fun checkPushNotificationsSupported() {
        check(card.capabilities.pushNotifications == true) {
            "Agent card reports that push notifications are not supported."
        }
    }
}
