package ai.koog.a2a.transport.jsonrpc

import ai.koog.a2a.annotations.InternalA2AApi
import ai.koog.a2a.consts.A2AVersions
import ai.koog.a2a.exceptions.A2AException
import ai.koog.a2a.exceptions.A2AInternalErrorException
import ai.koog.a2a.exceptions.A2AInvalidParamsException
import ai.koog.a2a.exceptions.A2AInvalidRequestException
import ai.koog.a2a.exceptions.A2AMethodNotFoundException
import ai.koog.a2a.exceptions.A2AParseException
import ai.koog.a2a.exceptions.A2AVersionNotSupportedException
import ai.koog.a2a.exceptions.ErrorData
import ai.koog.a2a.exceptions.ErrorInfo
import ai.koog.a2a.transport.A2AMethod
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
import ai.koog.a2a.validation.requireSupportedVersion
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.produceIn
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
 *
 * Every request is checked against [supportedVersions] before it is dispatched, see [checkVersion].
 *
 * @property requestHandler The handler responsible for processing A2A requests received by the transport.
 * @property supportedVersions A2A protocol versions this transport accepts in the `A2A-Version` header, compared by
 * `Major.Minor`. A missing or empty header is treated as version `0.3`, so it is rejected unless `0.3` is added here,
 * which does not make the SDK understand the 0.3 protocol. Defaults to [A2AVersions.SUPPORTED_VERSIONS].
 */
public open class JSONRPCServerTransport(
    override val requestHandler: RequestHandler,
    protected val supportedVersions: Set<String> = A2AVersions.SUPPORTED_VERSIONS,
) : ServerTransport {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    /**
     * Entry point for handling a raw JSON-RPC request payload.
     *
     * Parses the incoming JSON and resolves the A2A method, according to A2A spec, applies [customizeCallContext],
     * checks the requested protocol version with [checkVersion], then dispatches either to
     * [onRequest] for non-streaming methods or [onRequestStreaming] for streaming methods.
     *
     * @param requestRaw Raw request body
     * @param ctx Server call context associated with this request
     * @param respond Lambda to respond with a single non-streaming response. Also used for streaming methods
     * when they fail before the first event.
     * @param respondStreaming Lambda to respond with a streaming response. The flow must be collected, either within
     * the lambda or after it returns: this function returns only once the stream is consumed or its collection is
     * cancelled.
     * @param customizeCallContext Customizes [ctx] once the request is parsed, before the version is checked; not
     * called if the request cannot be parsed. An exception thrown here is answered like an error raised by the
     * [requestHandler]: an [A2AException] keeps its error code, any other exception becomes an internal error, and
     * the response carries the request id. Defaults to returning [ctx] unchanged.
     */
    public suspend fun handleRequest(
        requestRaw: String,
        ctx: ServerCallContext,
        respond: suspend (JSONRPCResponse) -> Unit,
        respondStreaming: suspend (Flow<JSONRPCResponse>) -> Unit,
        customizeCallContext: suspend (ServerCallContext) -> ServerCallContext = { it },
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
            val callCtx = customizeCallContext(ctx)

            // Applies to all methods; for streaming ones the error is still a plain response, as no stream is open yet
            checkVersion(callCtx)

            if (jsonRpcRequest.a2aMethod.streaming) {
                respondStreamingOrError(onRequestStreaming(jsonRpcRequest.request, callCtx), respond, respondStreaming)
            } else {
                respond(onRequest(jsonRpcRequest.request, callCtx))
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            respond(toJSONRPCErrorResponse(e, jsonRequest.requestId))
        }
    }

    /**
     * Checks that the protocol version requested by the client, [ServerCallContext.requestedVersion], is one of
     * the [supportedVersions]. Called for every request after its method is resolved.
     *
     * @throws A2AVersionNotSupportedException if the requested version is not supported.
     */
    protected open fun checkVersion(ctx: ServerCallContext) {
        requireSupportedVersion(ctx.requestedVersion, supportedVersions)
    }

    /**
     * Responds to a streaming request. An error raised before the first stream event is sent as a plain
     * JSON-RPC response with [respond], so the client gets it without a stream being opened.
     * Errors raised after the first event are sent as the last stream event.
     */
    private suspend fun respondStreamingOrError(
        responses: Flow<JSONRPCResponse>,
        respond: suspend (JSONRPCResponse) -> Unit,
        respondStreaming: suspend (Flow<JSONRPCResponse>) -> Unit,
    ): Unit = coroutineScope {
        val events = responses.buffer(Channel.RENDEZVOUS).produceIn(this)

        try {
            val first = events.receiveCatching()
            first.exceptionOrNull()?.let { throw it }

            when (val firstResponse = first.getOrNull()) {
                is JSONRPCErrorResponse -> respond(firstResponse)

                null -> respondStreaming(emptyFlow())

                // The stream can still be collected after respondStreaming returns (e.g. by Ktor's test engine),
                // so the events channel is not cancelled here; emitAll cancels it if the collection stops early.
                else -> respondStreaming(
                    flow {
                        emit(firstResponse)
                        emitAll(events)
                    }
                )
            }
        } catch (e: Throwable) {
            events.cancel()
            throw e
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
                A2AMethod.GetExtendedAgentCard.value ->
                    onRequest(request, ctx, requestHandler::onGetExtendedAgentCard)

                A2AMethod.SendMessage.value ->
                    onRequest(request, ctx, requestHandler::onSendMessage)

                A2AMethod.GetTask.value ->
                    onRequest(request, ctx, requestHandler::onGetTask)

                A2AMethod.ListTasks.value ->
                    onRequest(request, ctx, requestHandler::onListTasks)

                A2AMethod.CancelTask.value ->
                    onRequest(request, ctx, requestHandler::onCancelTask)

                A2AMethod.CreateTaskPushNotificationConfig.value ->
                    onRequest(request, ctx, requestHandler::onCreateTaskPushNotificationConfig)

                A2AMethod.GetTaskPushNotificationConfig.value ->
                    onRequest(request, ctx, requestHandler::onGetTaskPushNotificationConfig)

                A2AMethod.ListTaskPushNotificationConfigs.value ->
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
     * Missing params are treated as an empty object, so requests without required fields
     * (e.g. [ai.koog.a2a.model.GetExtendedAgentCardRequest]) can omit them.
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
        val params = request.params.takeIf { it !is JsonNull } ?: JsonObject(emptyMap())

        return try {
            JSONRPCJson.decodeFromJsonElement(serializer, params)
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
                    toErrorData(a2aException)
                )
            ),
            jsonrpc = JSONRPC_VERSION,
        )
    }

    /**
     * Build the `data` array entries for the error response from [A2AException.details].
     *
     * For A2A-specific errors, i.e. those with an [A2AException.reason], a leading [ErrorInfo] with that reason
     * and the `a2a-protocol.org` domain is added, unless the details already contain an [ErrorInfo],
     * [as per A2A spec](https://a2a-protocol.org/v1.0.1/specification/#95-error-handling).
     *
     * @param e Exception to build the error data for.
     */
    protected open fun toErrorData(e: A2AException): List<ErrorData> {
        val reason = e.reason

        return if (reason != null && e.details.none { it is ErrorInfo }) {
            listOf(ErrorInfo(reason = reason)) + e.details
        } else {
            e.details
        }
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
