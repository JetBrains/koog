package ai.koog.a2a.transport

import ai.koog.a2a.exceptions.A2AException
import ai.koog.a2a.exceptions.A2AInternalErrorException
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
    ): Flow<Response<Event>>

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
    ): Flow<Response<Event>>

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
 *     request: Request<MessageSendParams>,
 *     ctx: ServerCallContext
 * ): Response<CommunicationEvent> {
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

/**
 * Helper class to be used with [ServerCallContext.state] to store and retrieve values associated with a key in a typed
 * manner.
 *
 * @see ServerCallContext
 */
public class StateKey<@Suppress("unused") T>(public val name: String) {
    override fun toString(): String = "${super.toString()}(name=$name)"
}
