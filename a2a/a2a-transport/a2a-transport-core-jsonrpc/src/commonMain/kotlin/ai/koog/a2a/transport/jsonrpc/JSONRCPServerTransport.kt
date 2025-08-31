package ai.koog.a2a.transport.jsonrpc

import ai.koog.a2a.exceptions.A2AException
import ai.koog.a2a.exceptions.InvalidParamsException
import ai.koog.a2a.exceptions.MethodNotFoundException
import ai.koog.a2a.transport.Request
import ai.koog.a2a.transport.Response
import ai.koog.a2a.transport.ServerCallContext
import ai.koog.a2a.transport.ServerTransport
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCJson
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCRequest
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCSuccessResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Abstract transport implementation for JSON-RPC-based server communication.
 * Handles receiving JSON-RPC requests, processing them, and sending responses.
 */
public abstract class JSONRCPServerTransport : ServerTransport {
    /**
     * Handles a JSON-RPC request and returns the corresponding response.
     *
     * @throws A2AException if there's an error processing the request.
     */
    public suspend fun onRequest(
        request: JSONRPCRequest,
        ctx: ServerCallContext,
    ): JSONRPCSuccessResponse {
        return when (request.method) {
            A2AMethod.GetAuthenticatedExtendedAgentCard.value ->
                requestHandler.onGetAuthenticatedExtendedAgentCard(request.toRequest(), ctx).toJSONRPCResponse()

            A2AMethod.SendMessage.value ->
                requestHandler.onSendMessage(request.toRequest(), ctx).toJSONRPCResponse()

            A2AMethod.GetTask.value ->
                requestHandler.onGetTask(request.toRequest(), ctx).toJSONRPCResponse()

            A2AMethod.CancelTask.value ->
                requestHandler.onCancelTask(request.toRequest(), ctx).toJSONRPCResponse()

            A2AMethod.SetTaskPushNotificationConfig.value ->
                requestHandler.onSetTaskPushNotificationConfig(request.toRequest(), ctx).toJSONRPCResponse()

            A2AMethod.GetTaskPushNotificationConfig.value ->
                requestHandler.onGetTaskPushNotificationConfig(request.toRequest(), ctx).toJSONRPCResponse()

            A2AMethod.ListTaskPushNotificationConfig.value ->
                requestHandler.onListTaskPushNotificationConfig(request.toRequest(), ctx).toJSONRPCResponse()

            A2AMethod.DeleteTaskPushNotificationConfig.value ->
                requestHandler.onDeleteTaskPushNotificationConfig(request.toRequest(), ctx).toJSONRPCResponse()

            else -> throw MethodNotFoundException(request.method)
        }
    }

    /**
     * Handles a JSON-RPC request and returns the corresponding response stream.
     *
     * @throws A2AException if there's an error processing the request.
     */
    public suspend fun onRequestStreaming(
        request: JSONRPCRequest,
        ctx: ServerCallContext,
    ): Flow<JSONRPCSuccessResponse> {
        return when (request.method) {
            A2AMethod.SendMessageStreaming.value ->
                requestHandler.onSendMessageStreaming(request.toRequest(), ctx).map { it.toJSONRPCResponse() }

            else -> throw MethodNotFoundException(request.method)
        }
    }

    /**
     * Convert generic [JSONRPCRequest] to [Request].
     *
     * @throws InvalidParamsException if request params cannot be parsed to [T].
     */
    protected inline fun <reified T> JSONRPCRequest.toRequest(): Request<T> {
        val data = try {
            JSONRPCJson.decodeFromJsonElement<T>(params)
        } catch (_: SerializationException) {
            throw InvalidParamsException("Cannot parse request params to ${T::class}")
        }

        return Request(
            id = id,
            data = data
        )
    }

    /**
     * Convert generic [Response] to [JSONRPCSuccessResponse].
     */
    protected inline fun <reified T> Response<T>.toJSONRPCResponse(): JSONRPCSuccessResponse {
        return JSONRPCSuccessResponse(
            id = id,
            result = JSONRPCJson.encodeToJsonElement(data)
        )
    }
}
