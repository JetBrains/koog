package ai.koog.a2a.transport.server.jsonrpc.http

import ai.koog.a2a.model.AgentCard
import ai.koog.a2a.transport.ServerCallContext
import ai.koog.a2a.transport.jsonrpc.JSONRPCServerTransport
import ai.koog.a2a.transport.jsonrpc.serialization.JSONRPCJson
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
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
 *             a2aJsonRPCTransportRoute(firstTransport, "/agent-1")
 *             a2aJsonRPCTransportRoute(secondTransport, "/agent-2")
 *         }
 *
 *         // Agent card routes
 *         route("/agent-cards") {
 *             a2aAgentCardRoute(firstAgentCard, "/agent-1")
 *             a2aAgentCardRoute(secondAgentCard, "/agent-2")
 *         }
 *     }
 * }.startSuspend(wait = true)
 * ```
 *
 * @param path JSON-RPC endpoint path that will be mounted under the base route.
 * @param transport The JSON-RPC server transport to handle requests.
 */
public fun Route.a2aJsonRpcTransportRoute(
    path: String,
    transport: JSONRPCServerTransport,
): Route = route(path) {
    // Check SSE plugin is installed
    plugin(SSE)

    install(ContentNegotiation) {
        json(JSONRPCJson)
    }

    // Handle incoming JSON-RPC requests, both regular and streaming
    post {
        val ctx = ServerCallContext(
            headers = call.request.headers.toMap()
        )

        transport.handleRequest(
            requestRaw = call.receiveText(),
            ctx = ctx,
            respond = { response ->
                call.respond(response)
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
            }
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
