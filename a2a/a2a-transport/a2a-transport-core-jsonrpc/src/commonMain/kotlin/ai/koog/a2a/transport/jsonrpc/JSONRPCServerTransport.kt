package ai.koog.a2a.transport.jsonrpc

import ai.koog.a2a.annotations.InternalA2AApi
import ai.koog.a2a.exceptions.A2AException
import ai.koog.a2a.exceptions.A2AInternalErrorException
import ai.koog.a2a.exceptions.A2AInvalidParamsException
import ai.koog.a2a.exceptions.A2AInvalidRequestException
import ai.koog.a2a.exceptions.A2AMethodNotFoundException
import ai.koog.a2a.exceptions.A2AParseException
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
import ai.koog.a2a.transport.jsonrpc.model.JSONRPC_VERSION
import ai.koog.a2a.utils.runCatchingCancellable
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive

/**
 * Abstract transport implementation for JSON-RPC-based server communication.
 * Handles receiving JSON-RPC requests, processing them, and sending responses.
 */
public abstract class JSONRPCServerTransport : ServerTransport {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    /**
     * Manually parse [raw] string to build a [JSONRPCRequest] while throwing exceptions that A2A TCK excepts, according
     * to A2A specification.
     */
    protected fun parseJSONRPCRequest(raw: String): Pair<JSONRPCRequest, A2AMethod> {
        val jsonBody = try {
            JSONRPCJson.decodeFromString<JsonObject>(raw)
        } catch (e: SerializationException) {
            throw A2AParseException("Cannot parse request body to JSON:\n${e.message}")
        }

        // According to A2A TCK, need to parse id early to reply with provided id in error messages
        val id = jsonBody["id"]?.let {
            try {
                JSONRPCJson.decodeFromJsonElement<RequestId>(it)
            } catch (e: SerializationException) {
                throw A2AInvalidRequestException("Cannot parse request id to JSON-RPC id:\n${e.message}")
            }
        }

        val a2aMethod = (jsonBody["method"] as? JsonPrimitive)
            ?.content
            ?.let {
                A2AMethod.entries.find { m -> m.value == it }
                    ?: throw A2AMethodNotFoundException("Method not found: $it", id)
            }
            ?: throw A2AInvalidRequestException("No method parameter", id)

        val params = jsonBody["params"]
            ?.let {
                try {
                    JSONRPCJson
                        .decodeFromJsonElement<JsonObject>(it)
                        .also {
                            // According to A2A TCK, empty parameter names are not allowed
                            if (it.keys.any { it.isEmpty() }) {
                                throw A2AInvalidParamsException("Empty parameter names are not allowed", id)
                            }
                        }
                } catch (e: SerializationException) {
                    throw A2AInvalidParamsException("Cannot parse request params to JSON:\n${e.message}", id)
                }
            }

        val jsonrpc = jsonBody["jsonrpc"]
            ?.jsonPrimitive?.content
            ?.takeIf { it == JSONRPC_VERSION }
            ?: throw A2AInvalidRequestException("Unsupported JSON-RPC version", id)

        val jsonrpcBody = JSONRPCRequest(
            id = id ?: throw A2AInvalidRequestException("No id parameter"),
            method = a2aMethod.value,
            params = params ?: JsonNull,
            jsonrpc = jsonrpc,
        )

        return jsonrpcBody to a2aMethod
    }

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
                    throw A2AMethodNotFoundException("Non-streaming method not found: ${request.method}")
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
                flow { throw A2AMethodNotFoundException("Streaming method not found: ${request.method}") }
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
            result = JSONRPCJson.encodeToJsonElement(data),
            jsonrpc = JSONRPC_VERSION,
        )
    }

    /**
     * Handles exceptions, mapping all non [A2AException]s to [A2AInternalErrorException], and then converting them to [JSONRPCErrorResponse].
     */
    protected fun Throwable.toJSONRPCErrorResponse(requestId: RequestId? = null): JSONRPCErrorResponse {
        val a2aException: A2AException = when (this) {
            is A2AException -> this
            is CancellationException -> throw this
            is Exception -> {
                logger.warn(this) { "Non-A2A exception was detected when responding to request [requestId=$requestId]" }
                A2AInternalErrorException("Internal error: ${this.message}")
            }

            else -> throw this // Non-exception throwable shouldn't be handled, rethrowing it
        }

        return JSONRPCErrorResponse(
            id = requestId ?: a2aException.requestId, // if there's no requestId, use the one from the exception
            error = a2aException.toJSONRPCError(),
            jsonrpc = JSONRPC_VERSION,
        )
    }

    protected fun A2AException.toJSONRPCError(): JSONRPCError {
        return JSONRPCError(
            code = errorCode,
            message = message
        )
    }
}
