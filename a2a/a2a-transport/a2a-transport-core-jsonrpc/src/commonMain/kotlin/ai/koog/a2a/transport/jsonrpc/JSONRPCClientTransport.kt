package ai.koog.a2a.transport.jsonrpc

import ai.koog.a2a.exceptions.A2AException
import ai.koog.a2a.exceptions.ErrorData
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
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCErrorResponse
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCRequest
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCResponse
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCSuccessResponse
import ai.koog.a2a.transport.jsonrpc.model.JSONRPC_VERSION
import ai.koog.a2a.transport.jsonrpc.model.RequestId
import ai.koog.a2a.transport.jsonrpc.serialization.JSONRPCJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.serializer
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Abstract transport implementation for JSON-RPC-based client communication.
 * Handles sending JSON-RPC requests, processing responses, and mapping them to expected types.
 */
public abstract class JSONRPCClientTransport : ClientTransport {
    /**
     * Sends a JSON-RPC request and returns the corresponding response.
     */
    protected abstract suspend fun request(
        request: JSONRPCRequest,
        ctx: ClientCallContext,
    ): JSONRPCResponse

    /**
     * Sends a JSON-RPC request and returns the corresponding response stream.
     */
    protected abstract fun requestStreaming(
        request: JSONRPCRequest,
        ctx: ClientCallContext,
    ): Flow<JSONRPCResponse>

    /**
     * Convert generic request to [JSONRPCRequest].
     * You can override this method to customize request conversion, e.g., custom request id generation.
     *
     * @param request Request to convert.
     * @param serializer Serializer for the generic request.
     * @param method JSON-RPC method name
     */
    @OptIn(ExperimentalUuidApi::class)
    protected open fun <T> toJSONRPCRequest(
        request: T,
        serializer: KSerializer<T>,
        method: A2AMethod,
    ): JSONRPCRequest {
        return JSONRPCRequest(
            id = RequestId.StringId(Uuid.random().toString()),
            method = method.value,
            params = JSONRPCJson.encodeToJsonElement(serializer, request),
            jsonrpc = JSONRPC_VERSION,
        )
    }

    /**
     * Convert [JSONRPCResponse] to generic response.
     * You can override this method to customize response conversion, e.g., error handling.
     *
     * @param response Response to convert.
     * @param serializer Serializer for the generic response.
     * @throws A2AException if server returned an error, i.e., [JSONRPCErrorResponse]
     */
    protected open fun <T> toResponse(
        response: JSONRPCResponse,
        serializer: KSerializer<T>,
    ): T {
        return when (response) {
            is JSONRPCSuccessResponse ->
                JSONRPCJson.decodeFromJsonElement(serializer, response.result)

            is JSONRPCErrorResponse -> response.error.let {
                val details = JSONRPCJson.decodeFromJsonElement(ListSerializer(ErrorData.serializer()), it.data)
                throw A2AException.create(it.message, it.code, details)
            }
        }
    }

    /**
     * Generic request processing that uses [toJSONRPCRequest] and [toResponse].
     */
    protected suspend inline fun <reified TRequest, reified TResponse> request(
        method: A2AMethod,
        request: TRequest,
        ctx: ClientCallContext
    ): TResponse {
        val jsonrpcRequest = toJSONRPCRequest(request, serializer<TRequest>(), method)
        val jsonrpcResponse = request(jsonrpcRequest, ctx)

        return toResponse(jsonrpcResponse, serializer<TResponse>())
    }

    /**
     * Generic streaming request processing that uses [toJSONRPCRequest] and [toResponse].
     */
    protected inline fun <reified TRequest, reified TResponse> requestStreaming(
        method: A2AMethod,
        request: TRequest,
        ctx: ClientCallContext
    ): Flow<TResponse> {
        val jsonrpcRequest = toJSONRPCRequest(request, serializer<TRequest>(), method)
        val jsonrpcResponse = requestStreaming(jsonrpcRequest, ctx)

        return jsonrpcResponse
            .map { toResponse(it, serializer<TResponse>()) }
            .onCompletion { thr ->
                // Do not wrap A2A exceptions, propagate them directly
                if (thr?.cause is A2AException) {
                    throw thr.cause!!
                }
            }
    }

    override suspend fun getExtendedAgentCard(
        request: GetExtendedAgentCardRequest,
        ctx: ClientCallContext
    ): AgentCard = request(A2AMethod.GetAuthenticatedExtendedAgentCard, request, ctx)

    override suspend fun sendMessage(
        request: SendMessageRequest,
        ctx: ClientCallContext
    ): ResponseEvent = request(A2AMethod.SendMessage, request, ctx)

    override fun sendMessageStreaming(
        request: SendMessageRequest,
        ctx: ClientCallContext
    ): Flow<Event> = requestStreaming(A2AMethod.SendMessageStreaming, request, ctx)

    override suspend fun getTask(
        request: GetTaskRequest,
        ctx: ClientCallContext
    ): Task = request(A2AMethod.GetTask, request, ctx)

    override suspend fun listTasks(
        request: ListTasksRequest,
        ctx: ClientCallContext
    ): ListTasksResponse = request(A2AMethod.ListTasks, request, ctx)

    override suspend fun cancelTask(
        request: CancelTaskRequest,
        ctx: ClientCallContext
    ): Task = request(A2AMethod.CancelTask, request, ctx)

    override fun subscribeToTask(
        request: SubscribeToTaskRequest,
        ctx: ClientCallContext
    ): Flow<Event> = requestStreaming(A2AMethod.SubscribeToTask, request, ctx)

    override suspend fun createTaskPushNotificationConfig(
        request: TaskPushNotificationConfig,
        ctx: ClientCallContext
    ): TaskPushNotificationConfig = request(A2AMethod.CreateTaskPushNotificationConfig, request, ctx)

    override suspend fun getTaskPushNotificationConfig(
        request: GetTaskPushNotificationConfigRequest,
        ctx: ClientCallContext
    ): TaskPushNotificationConfig = request(A2AMethod.GetTaskPushNotificationConfig, request, ctx)

    override suspend fun listTaskPushNotificationConfigs(
        request: ListTaskPushNotificationConfigsRequest,
        ctx: ClientCallContext
    ): ListTaskPushNotificationConfigsResponse = request(A2AMethod.ListTaskPushNotificationConfig, request, ctx)

    override suspend fun deleteTaskPushNotificationConfig(
        request: DeleteTaskPushNotificationConfigRequest,
        ctx: ClientCallContext
    ) {
        request<DeleteTaskPushNotificationConfigRequest, Unit?>(A2AMethod.DeleteTaskPushNotificationConfig, request, ctx)
    }
}
