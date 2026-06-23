package ai.koog.a2a.transport.jsonrpc

import ai.koog.a2a.annotations.InternalA2AApi
import ai.koog.a2a.exceptions.A2AException
import ai.koog.a2a.exceptions.A2AInternalErrorException
import ai.koog.a2a.exceptions.A2AInvalidParamsException
import ai.koog.a2a.exceptions.A2AInvalidRequestException
import ai.koog.a2a.exceptions.A2AMethodNotFoundException
import ai.koog.a2a.exceptions.A2AParseException
import ai.koog.a2a.exceptions.ErrorData
import ai.koog.a2a.transport.RequestHandler
import ai.koog.a2a.transport.ServerCallContext
import ai.koog.a2a.transport.ServerTransport
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCError
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCErrorResponse
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCRequest
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCResponse
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCSuccessResponse
import ai.koog.a2a.transport.jsonrpc.model.JSONRPC_VERSION
import ai.koog.a2a.transport.jsonrpc.model.RequestId
import ai.koog.a2a.transport.jsonrpc.serialization.JSONRPCJson
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer

/**
 * Server transport implementation for JSON-RPC-based server communication.
 * Follows A2A specification in error handling.
 * Handles receiving JSON-RPC requests, processing them, and sending responses.
 *
 * [handleRequest] is an entry point for processing a raw request and replying with a final response.
 */
public open class JSONRPCServerTransport(
    override val requestHandler: RequestHandler,
) : ServerTransport {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    /**
     * Entry point for handling a raw JSON-RPC request payload.
     *
     * Parses the incoming JSON and resolves the A2A method, according to A2A spec, then dispatches either to
     * [onRequest] for non-streaming methods or [onRequestStreaming] for streaming methods.
     *
     * @param requestRaw Raw request body
     * @param ctx Server call context associated with this request
     * @param respond Lambda to respond with a single non-streaming response
     * @param respondStreaming Lambda to respond with a streaming response
     */
    public suspend fun handleRequest(
        requestRaw: String,
        ctx: ServerCallContext,
        respond: suspend (JSONRPCResponse) -> Unit,
        respondStreaming: suspend (Flow<JSONRPCResponse>) -> Unit,
    ) {
        // First step - parse the JSON request
        val jsonRequest = try {
            parseJsonRequest(requestRaw)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            respond(toJSONRPCErrorResponse(e))
            return
        }

        // Second step - parse the JSON-RPC request from JSON
        val jsonRpcRequest = try {
            parseJSONRPCRequest(jsonRequest)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            respond(toJSONRPCErrorResponse(e, jsonRequest.requestId))
            return
        }

        try {
            if (jsonRpcRequest.a2aMethod.streaming) {
                respondStreaming(onRequestStreaming(jsonRpcRequest.request, ctx))
            } else {
                respond(onRequest(jsonRpcRequest.request, ctx))
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            respond(toJSONRPCErrorResponse(e, jsonRequest.requestId))
        }
    }

    /**
     * Handles a non-streaming JSON-RPC request and returns the corresponding response
     * Handles exceptions, mapping all non [A2AException]s to [A2AInternalErrorException], and then converting them to [JSONRPCErrorResponse].
     */
    @OptIn(InternalA2AApi::class)
    protected open suspend fun onRequest(
        request: JSONRPCRequest,
        ctx: ServerCallContext,
    ): JSONRPCResponse {
        return try {
            when (request.method) {
                A2AMethod.GetAuthenticatedExtendedAgentCard.value ->
                    onRequest(request, ctx, requestHandler::onGetExtendedAgentCard)

                A2AMethod.SendMessage.value ->
                    onRequest(request, ctx, requestHandler::onSendMessage)

                A2AMethod.GetTask.value ->
                    onRequest(request, ctx, requestHandler::onGetTask)

                A2AMethod.ListTasks.value ->
                    onRequest(request, ctx, requestHandler::onListTasks)

                A2AMethod.CancelTask.value ->
                    onRequest(request, ctx, requestHandler::onCancelTask)

                A2AMethod.SubscribeToTask.value ->
                    onRequest(request, ctx, requestHandler::onSubscribeToTask)

                A2AMethod.CreateTaskPushNotificationConfig.value ->
                    onRequest(request, ctx, requestHandler::onCreateTaskPushNotificationConfig)

                A2AMethod.GetTaskPushNotificationConfig.value ->
                    onRequest(request, ctx, requestHandler::onGetTaskPushNotificationConfig)

                A2AMethod.ListTaskPushNotificationConfig.value ->
                    onRequest(request, ctx, requestHandler::onListTaskPushNotificationConfigs)

                A2AMethod.DeleteTaskPushNotificationConfig.value ->
                    onRequest(request, ctx, requestHandler::onDeleteTaskPushNotificationConfig)

                else ->
                    throw A2AMethodNotFoundException("Non-streaming method not found: ${request.method}")
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            toJSONRPCErrorResponse(e, request.id)
        }
    }

    /**
     * Handles a JSON-RPC request and returns the corresponding response stream.
     * Handles exceptions, mapping all non [A2AException]s to [A2AInternalErrorException], and then converting them to [JSONRPCErrorResponse].
     * Terminates the flow after the first exception.
     */
    protected open fun onRequestStreaming(
        request: JSONRPCRequest,
        ctx: ServerCallContext,
    ): Flow<JSONRPCResponse> {
        return when (request.method) {
            A2AMethod.SendMessageStreaming.value ->
                onRequestStreaming(request, ctx, requestHandler::onSendMessageStreaming)

            A2AMethod.SubscribeToTask.value ->
                onRequestStreaming(request, ctx, requestHandler::onSubscribeToTask)

            else ->
                flow { throw A2AMethodNotFoundException("Streaming method not found: ${request.method}") }
        }
            .map { it as JSONRPCResponse }
            .catch { thr ->
                when (thr) {
                    is CancellationException -> throw thr
                    is Exception -> emit(toJSONRPCErrorResponse(thr, request.id))
                    else -> throw thr
                }
            }
    }

    protected suspend inline fun <reified TRequest, reified TResponse> onRequest(
        request: JSONRPCRequest,
        ctx: ServerCallContext,
        action: suspend (TRequest, ServerCallContext) -> TResponse,
    ): JSONRPCSuccessResponse {
        val deserializedRequest = toRequest(request, serializer<TRequest>())
        val response = action(deserializedRequest, ctx)

        return toJSONRPCSuccessResponse(response, serializer<TResponse>(), request.id)
    }

    protected inline fun <reified TRequest, reified TResponse> onRequestStreaming(
        request: JSONRPCRequest,
        ctx: ServerCallContext,
        action: (TRequest, ServerCallContext) -> Flow<TResponse>,
    ): Flow<JSONRPCSuccessResponse> {
        val deserializedRequest = toRequest(request, serializer<TRequest>())
        val response = action(deserializedRequest, ctx)

        return response.map { toJSONRPCSuccessResponse(it, serializer<TResponse>(), request.id) }
    }

    /**
     * Convert [JSONRPCRequest] params to request [T].
     *
     * @param request Request to convert.
     * @param serializer Serializer for the request parameters.
     *
     * @throws A2AInvalidParamsException if request params cannot be parsed to [T].
     */
    protected open fun <T> toRequest(
        request: JSONRPCRequest,
        serializer: KSerializer<T>,
    ): T {
        return try {
            JSONRPCJson.decodeFromJsonElement(serializer, request.params)
        } catch (e: SerializationException) {
            throw A2AInvalidParamsException("Cannot parse request params:\n${e.message}")
        }
    }

    /**
     * Convert generic response to [JSONRPCSuccessResponse].
     * You can override this method to customize the serialization of the response.
     *
     * @param response Response to convert.
     * @param serializer Serializer for the response.
     * @param requestId Request ID.
     */
    protected open fun <T> toJSONRPCSuccessResponse(
        response: T,
        serializer: KSerializer<T>,
        requestId: RequestId,
    ): JSONRPCSuccessResponse {
        return JSONRPCSuccessResponse(
            id = requestId,
            result = JSONRPCJson.encodeToJsonElement(serializer, response),
            jsonrpc = JSONRPC_VERSION,
        )
    }

    /**
     * Convert exceptions, mapping all non [A2AException]s to [A2AInternalErrorException],
     * and then converting them to [JSONRPCErrorResponse].
     *
     * @param e Exception to convert.
     * @param requestId Request ID.
     */
    protected open fun toJSONRPCErrorResponse(
        e: Exception,
        requestId: RequestId? = null
    ): JSONRPCErrorResponse {
        val a2aException: A2AException = when (e) {
            is A2AException -> e

            is CancellationException -> throw e

            else -> {
                logger.warn(e) { "Non-A2A exception was detected when responding to request [requestId=$requestId]" }
                A2AInternalErrorException("Internal error: ${e.message}")
            }
        }

        return JSONRPCErrorResponse(
            id = requestId,
            error = JSONRPCError(
                code = a2aException.errorCode,
                message = a2aException.message,
                data = JSONRPCJson.encodeToJsonElement(
                    ListSerializer(ErrorData.serializer()),
                    a2aException.details
                )
            ),
            jsonrpc = JSONRPC_VERSION,
        )
    }

    /**
     * Result of the first step of parsing an A2A request - parsed JSON body and request ID.
     */
    protected class ParsedJsonRequest(
        public val body: JsonObject,
        public val requestId: RequestId?,
    )

    /**
     * Result of the second step of parsing an A2A request - parsed JSON-RPC request and A2A method.
     */
    protected class ParsedJSONRPCRequest(
        public val request: JSONRPCRequest,
        public val a2aMethod: A2AMethod,
    )

    /**
     * First step of parsing an A2A request - parse JSON body and request ID.
     *
     * @throws A2AException Exceptions that A2A TCK excepts, according to A2A specification.
     */
    protected fun parseJsonRequest(raw: String): ParsedJsonRequest {
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

        return ParsedJsonRequest(jsonBody, id)
    }

    /**
     * Second step of parsing an A2A request - parse JSON-RPC request and A2A method.
     *
     * @throws A2AException Exceptions that A2A TCK excepts, according to A2A specification.
     */
    protected fun parseJSONRPCRequest(jsonRequest: ParsedJsonRequest): ParsedJSONRPCRequest {
        val a2aMethod = (jsonRequest.body["method"] as? JsonPrimitive)
            ?.content
            ?.let {
                A2AMethod.entries.find { m -> m.value == it }
                    ?: throw A2AMethodNotFoundException("Method not found: $it")
            }
            ?: throw A2AInvalidRequestException("No method parameter")

        val params = jsonRequest.body["params"]
            ?.let {
                try {
                    JSONRPCJson
                        .decodeFromJsonElement<JsonObject>(it)
                        .also {
                            // According to A2A TCK, empty parameter names are not allowed
                            if (it.keys.any { it.isEmpty() }) {
                                throw A2AInvalidParamsException("Empty parameter names are not allowed")
                            }
                        }
                } catch (e: SerializationException) {
                    throw A2AInvalidParamsException("Cannot parse request params to JSON:\n${e.message}")
                }
            }

        val jsonrpc = jsonRequest.body["jsonrpc"]
            ?.jsonPrimitive?.content
            ?.takeIf { it == JSONRPC_VERSION }
            ?: throw A2AInvalidRequestException("Unsupported JSON-RPC version")

        val jsonrpcBody = JSONRPCRequest(
            id = jsonRequest.requestId ?: throw A2AInvalidRequestException("No id parameter"),
            method = a2aMethod.value,
            params = params ?: JsonNull,
            jsonrpc = jsonrpc,
        )

        return ParsedJSONRPCRequest(jsonrpcBody, a2aMethod)
    }
}
