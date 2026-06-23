package ai.koog.a2a.transport

import ai.koog.a2a.exceptions.A2AException
import ai.koog.a2a.exceptions.A2AInternalErrorException
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
import kotlinx.coroutines.flow.Flow

/**
 * Server transport processing raw requests made to [A2A protocol methods](https://a2a-protocol.org/v1.0.1/specification/#31-core-operations)
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
 * [A2A protocol methods](https://a2a-protocol.org/v1.0.1/specification/#31-core-operations).
 */
public interface RequestHandler {
    /**
     * Handles [GetExtendedAgentCard](https://a2a-protocol.org/v1.0.1/specification/#3111-get-extended-agent-card)
     *
     * @throws A2AException if there is an error with processsing the request.
     */
    public suspend fun onGetExtendedAgentCard(
        request: GetExtendedAgentCardRequest,
        ctx: ServerCallContext
    ): AgentCard

    /**
     * Handles [SendMessage](https://a2a-protocol.org/v1.0.1/specification/#311-send-message).
     *
     * @throws A2AException if there is an error with processsing the request.
     */
    public suspend fun onSendMessage(
        request: SendMessageRequest,
        ctx: ServerCallContext
    ): ResponseEvent

    /**
     * Handles [SendStreamingMessage](https://a2a-protocol.org/v1.0.1/specification/#312-send-streaming-message)
     *
     * @throws A2AException if there is an error with processsing the request.
     */
    public fun onSendMessageStreaming(
        request: SendMessageRequest,
        ctx: ServerCallContext
    ): Flow<Event>

    /**
     * Handles [GetTask](https://a2a-protocol.org/v1.0.1/specification/#313-get-task)
     *
     * @throws A2AException if there is an error with processsing the request.
     */
    public suspend fun onGetTask(
        request: GetTaskRequest,
        ctx: ServerCallContext
    ): Task

    /**
     * Handles [ListTasks](https://a2a-protocol.org/v1.0.1/specification/#314-list-tasks)
     *
     * @throws A2AException if there is an error with processsing the request.
     */
    public suspend fun onListTasks(
        request: ListTasksRequest,
        ctx: ServerCallContext
    ): ListTasksResponse

    /**
     * Handles [CancelTask](https://a2a-protocol.org/v1.0.1/specification/#315-cancel-task)
     *
     * @throws A2AException if there is an error with processsing the request.
     */
    public suspend fun onCancelTask(
        request: CancelTaskRequest,
        ctx: ServerCallContext
    ): Task

    /**
     * Handles [SubscribeToTask](https://a2a-protocol.org/v1.0.1/specification/#316-subscribe-to-task)
     *
     * @throws A2AException if there is an error with processsing the request.
     */
    public fun onSubscribeToTask(
        request: SubscribeToTaskRequest,
        ctx: ServerCallContext
    ): Flow<Event>

    /**
     * Handles [CreateTaskPushNotificationConfig](https://a2a-protocol.org/v1.0.1/specification/#317-create-push-notification-config)
     *
     * @throws A2AException if there is an error with processsing the request.
     */
    public suspend fun onCreateTaskPushNotificationConfig(
        request: TaskPushNotificationConfig,
        ctx: ServerCallContext
    ): TaskPushNotificationConfig

    /**
     * Handles [GetTaskPushNotificationConfig](https://a2a-protocol.org/v1.0.1/specification/#318-get-push-notification-config)
     *
     * @throws A2AException if there is an error with processsing the request.
     */
    public suspend fun onGetTaskPushNotificationConfig(
        request: GetTaskPushNotificationConfigRequest,
        ctx: ServerCallContext
    ): TaskPushNotificationConfig

    /**
     * Handles [ListTaskPushNotificationConfigs](https://a2a-protocol.org/v0.3.0/specification/#77-taskspushnotificationconfiglist)
     *
     * @throws A2AException if there is an error with processsing the request.
     */
    public suspend fun onListTaskPushNotificationConfigs(
        request: ListTaskPushNotificationConfigsRequest,
        ctx: ServerCallContext
    ): ListTaskPushNotificationConfigsResponse

    /**
     * Handles [DeleteTaskPushNotificationConfig](https://a2a-protocol.org/v1.0.1/specification/#3110-delete-push-notification-config)
     *
     * @throws A2AException if there is an error with processsing the request.
     */
    public suspend fun onDeleteTaskPushNotificationConfig(
        request: DeleteTaskPushNotificationConfigRequest,
        ctx: ServerCallContext
    )
}

/**
 * Represents the server context of a call.
 *
 * This context has [state] associated with it, which is essentially an untyped map. It can be used to store arbitrary
 * user-defined data. This is useful for extending the base logic with business-dependent logic, e.g., storing user
 * information to authorize particular requests. This untyped [state] map has typed accessors for more convenient access,
 * so it is recommended to use them when reading from state: [getFromState], [getFromStateOrNull].
 *
 * **Note**: Make sure the types of [StateKey] and the value match when populating [state], otherwise [getFromState]
 * and [getFromStateOrNull] will throw [IllegalStateException].
 *
 * Example usage:
 * ```kotlin
 * // User-defined data class
 * data class User(val id: String)
 *
 * // Collection of user-defined state keys
 * object StateKeys {
 *     val USER_KEY = StateKey<User>("42")
 * }
 *
 * // On the handler side - copying supplied context and populating state
 * override suspend fun onSendMessage(
 *     request: SendMessageRequest,
 *     ctx: ServerCallContext
 * ): ResponseEvent {
 *    val user = ctx.headers.getValue("user-id").let { User(it) }
 *    val newCtx = ctx.copy(state = ctx.state + (StateKeys.USER_KEY to user))
 *
 *    super.onSendMessage(request, newCtx)
 * }
 *
 * // On the business logic side - retrieving user data from context
 * val user = ctx.getFromState(StateKeys.USER_KEY)
 * ```
 *
 * @property headers Headers associated with the call.
 * @property state State associated with the call, allows storing arbitrary values. To get typed value from the state,
 * use [getFromState] or [getFromStateOrNull] with appropriate [StateKey].
 */
public class ServerCallContext(
    public val headers: Map<String, List<String>> = emptyMap(),
    public val state: Map<StateKey<*>, Any> = emptyMap()
) {
    /**
     * Retrieves a value of type [T] associated with the specified [key] from the [state] map.
     * If the [key] is not found in the state, returns `null`.
     *
     * Performs unsafe cast under the hood, so make sure the value is of the expected type.
     *
     * @param key The state key for which the associated value needs to be retrieved.
     */
    public inline fun <reified T> getFromStateOrNull(key: StateKey<T>): T? {
        return state[key]?.let {
            it as? T ?: throw IllegalStateException("State value for key $key is not of expected type ${T::class}")
        }
    }

    /**
     * Retrieves a value of type [T] associated with the specified [key] from the [state] map.
     *
     * Performs unsafe cast under the hood, so make sure the value is of the expected type.
     *
     * @param key The state key for which the associated value needs to be retrieved.
     * @throws NoSuchElementException if the [key] is not found in the state.
     */
    public inline fun <reified T> getFromState(key: StateKey<T>): T {
        return getFromStateOrNull(key) ?: throw NoSuchElementException("State key $key not found or null")
    }

    /**
     * Creates a copy of this [ServerCallContext].
     */
    public fun copy(
        headers: Map<String, List<String>> = this.headers,
        state: Map<StateKey<*>, Any> = this.state,
    ): ServerCallContext = ServerCallContext(headers, state)
}

