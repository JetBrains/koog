package ai.koog.a2a.transport.jsonrpc

import ai.koog.a2a.annotations.InternalA2AApi
import ai.koog.a2a.exceptions.A2AException
import ai.koog.a2a.exceptions.A2AInternalErrorException
import ai.koog.a2a.exceptions.A2AInvalidParamsException
import ai.koog.a2a.exceptions.A2AMethodNotFoundException
import ai.koog.a2a.transport.Request
import ai.koog.a2a.transport.RequestId
import ai.koog.a2a.transport.Response
import ai.koog.a2a.transport.ServerCallContext
import ai.koog.a2a.transport.ServerTransport
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCError
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCErrorResponse
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCJson
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCRequest
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCResponse
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCSuccessResponse
import ai.koog.a2a.utils.runCatchingCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Abstract transport implementation for JSON-RPC-based server communication.
 * Handles receiving JSON-RPC requests, processing them, and sending responses.
 */
public abstract class JSONRPCServerTransport : ServerTransport {
    /**
     * Handles a JSON-RPC request and returns the corresponding response
     * Handles exceptions, mapping all non [A2AException]s to [A2AInternalErrorException], and then converting them to [JSONRPCErrorResponse].
     */
    @OptIn(InternalA2AApi::class)
    protected suspend fun onRequest(
        request: JSONRPCRequest,
        ctx: ServerCallContext,
    ): JSONRPCResponse {
        return runCatchingCancellable {
            when (request.method) {
                A2AMethod.GetAuthenticatedExtendedAgentCard.value ->
                    requestHandler.onGetAuthenticatedExtendedAgentCard(request.toRequest(), ctx)
                        .toJSONRPCSuccessResponse()

                A2AMethod.SendMessage.value ->
                    requestHandler.onSendMessage(request.toRequest(), ctx).toJSONRPCSuccessResponse()

                A2AMethod.GetTask.value ->
                    requestHandler.onGetTask(request.toRequest(), ctx).toJSONRPCSuccessResponse()

                A2AMethod.CancelTask.value ->
                    requestHandler.onCancelTask(request.toRequest(), ctx).toJSONRPCSuccessResponse()

                A2AMethod.SetTaskPushNotificationConfig.value ->
                    requestHandler.onSetTaskPushNotificationConfig(request.toRequest(), ctx).toJSONRPCSuccessResponse()

                A2AMethod.GetTaskPushNotificationConfig.value ->
                    requestHandler.onGetTaskPushNotificationConfig(request.toRequest(), ctx).toJSONRPCSuccessResponse()

                A2AMethod.ListTaskPushNotificationConfig.value ->
                    requestHandler.onListTaskPushNotificationConfig(request.toRequest(), ctx).toJSONRPCSuccessResponse()

                A2AMethod.DeleteTaskPushNotificationConfig.value ->
                    requestHandler.onDeleteTaskPushNotificationConfig(request.toRequest(), ctx)
                        .toJSONRPCSuccessResponse()

                else ->
                    throw A2AMethodNotFoundException("Method not found: ${request.method}")
            }
        }.getOrElse { it.toJSONRPCErrorResponse(request.id) }
    }

    /**
     * Handles a JSON-RPC request and returns the corresponding response stream.
     * Handles exceptions, mapping all non [A2AException]s to [A2AInternalErrorException], and then converting them to [JSONRPCErrorResponse].
     * Terminates the flow after the first exception.
     */
    protected fun onRequestStreaming(
        request: JSONRPCRequest,
        ctx: ServerCallContext,
    ): Flow<JSONRPCResponse> {
        return when (request.method) {
            A2AMethod.SendMessageStreaming.value ->
                requestHandler.onSendMessageStreaming(request.toRequest(), ctx)

            A2AMethod.ResubscribeTask.value ->
                requestHandler.onResubscribeTask(request.toRequest(), ctx)

            else ->
                flow { throw A2AMethodNotFoundException("Method not found: ${request.method}") }
        }.map { it.toJSONRPCSuccessResponse() as JSONRPCResponse }
            .catch { emit(it.toJSONRPCErrorResponse(request.id)) }
    }

    /**
     * Convert generic [JSONRPCRequest] to [Request].
     *
     * @throws A2AInvalidParamsException if request params cannot be parsed to [T].
     */
    protected inline fun <reified T> JSONRPCRequest.toRequest(): Request<T> {
        val data = try {
            JSONRPCJson.decodeFromJsonElement<T>(params)
        } catch (e: SerializationException) {
            throw A2AInvalidParamsException("Cannot parse request params:\n${e.message}")
        }

        return Request(
            id = id,
            data = data
        )
    }

    /**
     * Convert generic [Response] to [JSONRPCSuccessResponse].
     */
    protected inline fun <reified T> Response<T>.toJSONRPCSuccessResponse(): JSONRPCSuccessResponse {
        return JSONRPCSuccessResponse(
            id = id,
            result = JSONRPCJson.encodeToJsonElement(data)
        )
    }

    /**
     * Handles exceptions, mapping all non [A2AException]s to [A2AInternalErrorException], and then converting them to [JSONRPCErrorResponse].
     */
    protected fun Throwable.toJSONRPCErrorResponse(requestId: RequestId? = null): JSONRPCErrorResponse {
        val a2aException: A2AException = when (this) {
            is A2AException -> this
            is Exception -> A2AInternalErrorException("Internal error: ${this.message}")
            else -> throw this // Non-exception throwable shouldn't be handled, rethrowing it
        }

        return JSONRPCErrorResponse(
            id = requestId,
            error = a2aException.toJSONRPCError()
        )
    }

    protected fun A2AException.toJSONRPCError(): JSONRPCError {
        return JSONRPCError(
            code = errorCode,
            message = message
        )
    }
}
