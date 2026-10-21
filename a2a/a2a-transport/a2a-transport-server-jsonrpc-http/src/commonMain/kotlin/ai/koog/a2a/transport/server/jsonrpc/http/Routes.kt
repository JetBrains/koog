package ai.koog.a2a.transport.server.jsonrpc.http

import ai.koog.a2a.model.AgentCard
import ai.koog.a2a.transport.ServerCallContext
import ai.koog.a2a.transport.jsonrpc.JSONRPCServerTransport
import ai.koog.a2a.transport.jsonrpc.serialization.JSONRPCJson
import ai.koog.a2a.transport.withCaseInsensitiveNames
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.sse.SSE
import io.ktor.server.sse.SSEServerContent
import io.ktor.sse.ServerSentEvent
import io.ktor.util.toMap

/**
 * Route for handling JSON-RPC HTTP requests.
 * Allows mounting A2A requests handling into an existing Ktor server application.
 * This can also be used to mount multiple A2A server transports on the same server, to serve multiple A2A agents.
 *
 * You also need to serve an [ai.koog.a2a.model.AgentCard] manually to make your agent discoverable at the desired location.
 * [a2aAgentCardRoute] can be used for that.
 *
 * For a provided standalone Ktor server for a single agent for a quick prototyping, check [HttpJSONRPCServerTransport]
 *
 * Example usage:
 * ```kotlin
 * val firstAgentCard = AgentCard(...)
 * val firstTransport = JSONRPCServerTransport(A2AServer(...)) // first agent
 *
 * val secondAgentCard = AgentCard(...)
 * val secondTransport = JSONRPCServerTransport(A2AServer(...)) // second agent
 *
 * embeddedServer(Netty, port = 8080) {
 *     install(SSE)
 *
 *     // Other configurations...
 *
 *     routing {
 *         // Other routes...
 *
 *         // Transport agent routes
 *         route("/a2a") {
 *             a2aJsonRpcTransportRoute("/agent-1", firstTransport)
 *             a2aJsonRpcTransportRoute("/agent-2", secondTransport)
 *         }
 *
 *         // Agent card routes
 *         route("/agent-cards") {
 *             a2aAgentCardRoute("/agent-1", firstAgentCard)
 *             a2aAgentCardRoute("/agent-2", secondAgentCard)
 *         }
 *     }
 * }.startSuspend(wait = true)
 * ```
 *
 * [customizeCallContext] can enrich the [ServerCallContext] built from the request headers, e.g. with the
 * authenticated principal:
 * ```kotlin
 * val UserKey = StateKey<String>("user")
 *
 * authenticate("auth-basic") {
 *     a2aJsonRpcTransportRoute("/agent", transport) { ctx ->
 *         ctx.copy(state = ctx.state + (UserKey to principal<UserIdPrincipal>()!!.name))
 *     }
 * }
 * ```
 *
 * @param path JSON-RPC endpoint path that will be mounted under the base route.
 * @param transport The JSON-RPC server transport to handle requests.
 * @param customizeCallContext Called for every parsed JSON-RPC request with the current [ApplicationCall] as the
 * receiver and the default context built from the request headers; the returned context is passed to the
 * [transport]. It runs before the requested version is checked. An [ai.koog.a2a.exceptions.A2AException] thrown
 * here is answered as a JSON-RPC error with its code, any other exception as an internal error.
 * Defaults to returning the default context unchanged.
 */
public fun Route.a2aJsonRpcTransportRoute(
    path: String,
    transport: JSONRPCServerTransport,
    customizeCallContext: suspend ApplicationCall.(ServerCallContext) -> ServerCallContext = { it },
): Route = route(path) {
    // Check SSE plugin is installed
    plugin(SSE)

    install(ContentNegotiation) {
        json(JSONRPCJson)
    }

    // Handle incoming JSON-RPC requests, both regular and streaming
    post {
        // HTTP header names are case-insensitive (HTTP/2 lowercases them), but Ktor's toMap() keeps names as received
        val defaultCtx = ServerCallContext(
            headers = call.request.headers.toMap().withCaseInsensitiveNames()
        )

        transport.handleRequest(
            requestRaw = call.receiveText(),
            ctx = defaultCtx,
            respond = { response ->
                // Bypass content negotiation: streaming requests send `Accept: text/event-stream`,
                // but errors raised before the stream starts are still answered with plain JSON
                call.respondText(JSONRPCJson.encodeToString(response), ContentType.Application.Json)
            },
            respondStreaming = { responseFlow ->
                // Reply with SSE (implementation copied from SSE plugin code)
                call.response.apply {
                    header(HttpHeaders.ContentType, ContentType.Text.EventStream.toString())
                    header(HttpHeaders.CacheControl, "no-store")
                    header(HttpHeaders.Connection, "keep-alive")
                    header("X-Accel-Buffering", "no")
                }

                call.respond(
                    message = SSEServerContent(call) {
                        responseFlow.collect { response ->
                            send(ServerSentEvent(JSONRPCJson.encodeToString(response)))
                        }
                    }
                )
            },
            customizeCallContext = { call.customizeCallContext(it) },
        )
    }
}

/**
 * Route for serving an [ai.koog.a2a.model.AgentCard] at the specified path.
 */
public fun Route.a2aAgentCardRoute(
    path: String,
    agentCard: AgentCard,
): Route = route(path) {
    install(ContentNegotiation) {
        json(JSONRPCJson)
    }

    get {
        call.respond(agentCard)
    }
}
