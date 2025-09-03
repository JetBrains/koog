package ai.koog.a2a.transport.server.jsonrpc.http

import ai.koog.a2a.exceptions.A2AInvalidRequestException
import ai.koog.a2a.exceptions.A2AParseException
import ai.koog.a2a.transport.RequestHandler
import ai.koog.a2a.transport.ServerCallContext
import ai.koog.a2a.transport.jsonrpc.JSONRPCServerTransport
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCJson
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCRequest
import ai.koog.a2a.utils.runCatchingCancellable
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.pluginOrNull
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.sse.SSE
import io.ktor.server.sse.send
import io.ktor.server.sse.sse
import io.ktor.util.toMap
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.serializer

/**
 * Implements JSON-RPC server transport over HTTP, supporting standard JSON-RPC
 * request/response and server-sent events for streaming responses.
 *
 * The transport integrates with Ktor routing to define and manage routes for
 * JSON-RPC endpoints. It ensures compliance with the A2A specification for
 * error handling and JSON-RPC request processing.
 *
 * @property requestHandler The handler responsible for processing JSON-RPC requests
 * received by the transport.
 */
public class HttpJSONRPCServerTransport(
    override val requestHandler: RequestHandler,
) : JSONRPCServerTransport() {

    /**
     * Routes for handling JSON-RPC HTTP requests.
     * Follows A2A specification in error handling.
     *
     * @param path JSON-RPC endpoint path.
     */
    public fun Route.transportRoutes(path: String): Route = route(path) {
        if (application.pluginOrNull(SSE) == null) {
            throw IllegalStateException("SSE plugin must be installed in the application to add these routes.")
        }

        install(ContentNegotiation) {
            json(JSONRPCJson)
        }

        post {
            runCatchingCancellable {
                onRequest(
                    request = call.receiveJSONRPCRequest(),
                    ctx = call.toServerCallContext()
                )
            }.getOrElse { it.toJSONRPCErrorResponse() }
        }

        sse(
            serialize = { typeInfo, it ->
                val kType = typeInfo.kotlinType ?: throw IllegalArgumentException("Null KType for value: $it")
                val serializer = JSONRPCJson.serializersModule.serializer(kType)
                JSONRPCJson.encodeToString(serializer, it)
            }
        ) {
            runCatchingCancellable {
                onRequestStreaming(
                    request = call.receiveJSONRPCRequest(),
                    ctx = call.toServerCallContext()
                ).collect { response -> send(response) }
            }.getOrElse {
                send(it.toJSONRPCErrorResponse())
            }
        }
    }

    /**
     * Converts raw request body to [JSONRPCRequest], following A2A specification for error handling.
     */
    private suspend fun ApplicationCall.receiveJSONRPCRequest(): JSONRPCRequest {
        val jsonBody = try {
            receive<JsonElement>()
        } catch (e: SerializationException) {
            throw A2AParseException("Cannot parse request body to JSON:\n${e.message}")
        }

        return try {
            JSONRPCJson.decodeFromJsonElement<JSONRPCRequest>(jsonBody)
        } catch (e: SerializationException) {
            throw A2AInvalidRequestException("Cannot parse request params to JSON-RPC request:\n${e.message}")
        }
    }

    private fun ApplicationCall.toServerCallContext(): ServerCallContext {
        return ServerCallContext(
            headers = request.headers.toMap()
        )
    }
}
