package ai.koog.a2a.transport.jsonrpc

import ai.koog.a2a.exceptions.A2AException
import ai.koog.a2a.exceptions.createA2AException
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
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCError
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCErrorResponse
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCJson
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCRequest
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCResponse
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCSuccessResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Abstract transport implementation for JSON-RPC-based client communication.
 * Handles sending JSON-RPC requests, processing responses, and mapping them to expected types.
 */
public abstract class JSONRPCClientTransport : ClientTransport {
    /**
     * Sends a JSON-RPC request and returns the corresponding response.
     *
     * @throws A2AException if server returned an error.
     */
    public abstract suspend fun request(
        request: JSONRPCRequest,
        ctx: ClientCallContext,
    ): JSONRPCResponse

    /**
     * Sends a JSON-RPC request and returns the corresponding response stream.
     *
     * @throws A2AException if server returned an error.
     */
    public abstract suspend fun requestStreaming(
        request: JSONRPCRequest,
        ctx: ClientCallContext,
    ): Flow<JSONRPCResponse>

    /**
     * Convert generic [Request] to [JSONRPCRequest].
     */
    protected inline fun <reified T> Request<T>.toJSONRPCRequest(method: A2AMethod): JSONRPCRequest {
        return JSONRPCRequest(
            id = id,
            method = method.value,
            params = JSONRPCJson.encodeToJsonElement<T>(data)
        )
    }

    /**
     * Convert [JSONRPCResponse] to generic [Response].
     *
     * @throws A2AException if server returned an error.
     */
    protected inline fun <reified T> JSONRPCResponse.toResponse(): Response<T> {
        return when (this) {
            is JSONRPCSuccessResponse -> Response(
                id = id,
                data = JSONRPCJson.decodeFromJsonElement(result),
            )

            is JSONRPCErrorResponse -> {
                throw error.toA2AException()
            }
        }
    }

    protected fun JSONRPCError.toA2AException(): A2AException {
        return createA2AException(message, code)
    }

    /**
     * Generic request processing.
     */
    protected suspend inline fun <reified TRequest, reified TResponse> request(
        method: A2AMethod,
        request: Request<TRequest>,
        ctx: ClientCallContext
    ): Response<TResponse> {
        val jsonrpcRequest = request.toJSONRPCRequest(method)
        val jsonrpcResponse = request(jsonrpcRequest, ctx)

        return jsonrpcResponse.toResponse<TResponse>()
    }

    /**
     * Generic streaming request processing.
     */
    protected suspend inline fun <reified TRequest, reified TResponse> requestStreaming(
        method: A2AMethod,
        request: Request<TRequest>,
        ctx: ClientCallContext
    ): Flow<Response<TResponse>> {
        val jsonrpcRequest = request.toJSONRPCRequest(method)
        val jsonrpcResponse = requestStreaming(jsonrpcRequest, ctx)

        return jsonrpcResponse.map { it.toResponse<TResponse>() }
    }

    override suspend fun getAuthenticatedExtendedAgentCard(
        request: Request<Nothing?>,
        ctx: ClientCallContext
    ): Response<AgentCard> =
        request(A2AMethod.GetAuthenticatedExtendedAgentCard, request, ctx)

    override suspend fun sendMessage(
        request: Request<MessageSendParams>,
        ctx: ClientCallContext
    ): Response<CommunicationEvent> =
        request(A2AMethod.SendMessage, request, ctx)

    override suspend fun sendMessageStreaming(
        request: Request<MessageSendParams>,
        ctx: ClientCallContext
    ): Flow<Response<UpdateEvent>> =
        requestStreaming(A2AMethod.SendMessageStreaming, request, ctx)

    override suspend fun getTask(
        request: Request<TaskQueryParams>,
        ctx: ClientCallContext
    ): Response<Task> =
        request(A2AMethod.GetTask, request, ctx)

    override suspend fun cancelTask(
        request: Request<TaskIdParams>,
        ctx: ClientCallContext
    ): Response<Task> =
        request(A2AMethod.CancelTask, request, ctx)

    override suspend fun setTaskPushNotificationConfig(
        request: Request<TaskPushNotificationConfig>,
        ctx: ClientCallContext
    ): Response<TaskPushNotificationConfig> =
        request(A2AMethod.SetTaskPushNotificationConfig, request, ctx)

    override suspend fun getTaskPushNotificationConfig(
        request: Request<TaskPushNotificationConfigParams>,
        ctx: ClientCallContext
    ): Response<TaskPushNotificationConfig> =
        request(A2AMethod.GetTaskPushNotificationConfig, request, ctx)

    override suspend fun listTaskPushNotificationConfig(
        request: Request<TaskIdParams>,
        ctx: ClientCallContext
    ): Response<List<TaskPushNotificationConfig>> =
        request(A2AMethod.ListTaskPushNotificationConfig, request, ctx)

    override suspend fun deleteTaskPushNotificationConfig(
        request: Request<TaskPushNotificationConfigParams>,
        ctx: ClientCallContext
    ): Response<Unit> =
        request(A2AMethod.DeleteTaskPushNotificationConfig, request, ctx)
}
