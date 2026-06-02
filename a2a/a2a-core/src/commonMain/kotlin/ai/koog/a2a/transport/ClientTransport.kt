package ai.koog.a2a.transport

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
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerializationException

/**
 * Client transport making requests to [A2A protocol methods](https://a2a-protocol.org/v1.0.1/specification/#31-core-operations)
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
     * Calls [GetExtendedAgentCard](https://a2a-protocol.org/v1.0.1/specification/#3111-get-extended-agent-card)
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun getExtendedAgentCard(
        request: GetExtendedAgentCardRequest,
        ctx: ClientCallContext = ClientCallContext.Default
    ): AgentCard

    /**
     * Calls [SendMessage](https://a2a-protocol.org/v1.0.1/specification/#311-send-message).
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun sendMessage(
        request: SendMessageRequest,
        ctx: ClientCallContext = ClientCallContext.Default
    ): ResponseEvent

    /**
     * Calls [SendStreamingMessage](https://a2a-protocol.org/v1.0.1/specification/#312-send-streaming-message)
     *
     * @throws A2AException if server returned an error.
     */
    public fun sendMessageStreaming(
        request: SendMessageRequest,
        ctx: ClientCallContext = ClientCallContext.Default
    ): Flow<Event>

    /**
     * Calls [GetTask](https://a2a-protocol.org/v1.0.1/specification/#313-get-task)
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun getTask(
        request: GetTaskRequest,
        ctx: ClientCallContext = ClientCallContext.Default
    ): Task

    /**
     * Calls [ListTasks](https://a2a-protocol.org/v1.0.1/specification/#314-list-tasks)
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun listTasks(
        request: ListTasksRequest,
        ctx: ClientCallContext = ClientCallContext.Default
    ): ListTasksResponse

    /**
     * Calls [CancelTask](https://a2a-protocol.org/v1.0.1/specification/#315-cancel-task)
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun cancelTask(
        request: CancelTaskRequest,
        ctx: ClientCallContext = ClientCallContext.Default
    ): Task

    /**
     * Calls [SubscribeToTask](https://a2a-protocol.org/v1.0.1/specification/#316-subscribe-to-task)
     *
     * @throws A2AException if server returned an error.
     */
    public fun subscribeToTask(
        request: SubscribeToTaskRequest,
        ctx: ClientCallContext = ClientCallContext.Default
    ): Flow<Event>

    /**
     * Calls [CreateTaskPushNotificationConfig](https://a2a-protocol.org/v1.0.1/specification/#317-create-push-notification-config)
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun createTaskPushNotificationConfig(
        request: TaskPushNotificationConfig,
        ctx: ClientCallContext = ClientCallContext.Default
    ): TaskPushNotificationConfig

    /**
     * Calls [GetTaskPushNotificationConfig](https://a2a-protocol.org/v1.0.1/specification/#318-get-push-notification-config)
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun getTaskPushNotificationConfig(
        request: GetTaskPushNotificationConfigRequest,
        ctx: ClientCallContext = ClientCallContext.Default
    ): TaskPushNotificationConfig

    /**
     * Calls [ListTaskPushNotificationConfigs](https://a2a-protocol.org/v0.3.0/specification/#77-taskspushnotificationconfiglist)
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun listTaskPushNotificationConfigs(
        request: ListTaskPushNotificationConfigsRequest,
        ctx: ClientCallContext = ClientCallContext.Default
    ): ListTaskPushNotificationConfigsResponse

    /**
     * Calls [DeleteTaskPushNotificationConfig](https://a2a-protocol.org/v1.0.1/specification/#3110-delete-push-notification-config)
     *
     * @throws A2AException if server returned an error.
     */
    public suspend fun deleteTaskPushNotificationConfig(
        request: DeleteTaskPushNotificationConfigRequest,
        ctx: ClientCallContext = ClientCallContext.Default
    )
}

/**
 * Represents the client context of a call.
 *
 * This context has [state] associated with it, which is essentially an untyped map. It can be used to pass arbitrary
 * user-defined data. This is useful for extending the base logic with business-dependent logic, e.g., passing user
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
 * // On the caller side - create a context and provide user data
 * val client: A2AClient = ...
 * val user = User("42")
 * val ctx = ClientCallContext(state = mapOf(StateKeys.USER_KEY to user))
 * client.sendMessage(SendMessageRequest(...), ctx)
 *
 * // Inside a custom ClientTransport/A2AClient override
 *
 * // Extending existing client transport to add custom logic
 * class CustomClientTransport : HttpJSONRPCClientTransport(...) {
 *   override suspend fun request(
 *       request: JSONRPCRequest,
 *       ctx: ClientCallContext
 *   ): JSONRPCResponse {
 *         val user: User = ctx.getFromState(StateKeys.USER_KEY)
 *         // Use retrieved information to construct a custom request: adding query parameters, modifying headers, etc.
 *         // ...
 *     }
 * }
 * ```
 *
 * @property headers Headers associated with the call.
 * @property state State associated with the call, allows storing arbitrary values. To get typed value from the state,
 * use [getFromState] or [getFromStateOrNull] with appropriate [StateKey].
 */
public class ClientCallContext(
    public val headers: Map<String, List<String>> = emptyMap(),
    public val state: Map<StateKey<*>, Any> = emptyMap(),
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
     * Creates a copy of this [ClientCallContext].
     */
    public fun copy(
        headers: Map<String, List<String>> = this.headers,
        state: Map<StateKey<*>, Any> = this.state,
    ): ClientCallContext = ClientCallContext(headers, state)

    @Suppress("MissingKDocForPublicAPI")
    public companion object {
        public val Default: ClientCallContext = ClientCallContext()
    }
}
