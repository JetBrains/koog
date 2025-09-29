package ai.koog.a2a.transport.server.jsonrpc.http

import ai.koog.a2a.annotations.InternalA2AApi
import ai.koog.a2a.consts.A2AConsts
import ai.koog.a2a.model.AgentCard
import ai.koog.a2a.transport.RequestHandler
import ai.koog.a2a.transport.ServerCallContext
import ai.koog.a2a.transport.jsonrpc.JSONRPCServerTransport
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCJson
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCRequest
import ai.koog.a2a.utils.runCatchingCancellable
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.ApplicationEngineFactory
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.SSEServerContent
import io.ktor.server.sse.ServerSSESession
import io.ktor.sse.ServerSentEvent
import io.ktor.util.toMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Implements A2A JSON-RPC server transport over HTTP using Ktor server
 * It ensures compliance with the A2A specification for error handling and JSON-RPC request processing.
 * This transport can be used either as a standalone server or integrated into an existing Ktor application.
 *
 * Example usage as a standalone server:
 * ```kotlin
 * val transport = HttpJSONRPCServerTransport(
 *     requestHandler = A2AServer(...)
 * )
 *
 * transport.start(Netty, 8080, "/my-agent", agentCard = AgentCard(...), agentCardPath = "/my-agent-card.json")
 * transport.stop()
 * ```
 *
 * Example usage as an integration into an existing Ktor server.
 * Can also be used to integrate multiple A2A server transports on the same server, to serve multiple A2A agents:
 * ```kotlin
 * val agentOneTransport = HttpJSONRPCServerTransport(
 *     requestHandler = A2AServer(...)
 * )
 * val agentTwoTransport = HttpJSONRPCServerTransport(
 *     requestHandler = A2AServer(...)
 * )
 *
 * embeddedServer(Netty, port = 8080) {
 *     install(SSE)
 *
 *     // Other configurations...
 *
 *     routing {
 *         // Other routes...
 *
 *         route("/a2a") {
 *             agentOneTransport.transportRoutes(this, "/agent-1")
 *             agentTwoTransport.transportRoutes(this, "/agent-2")
 *         }
 *     }
 * }.startSuspend(wait = true)
 * ```
 *
 * @property requestHandler The handler responsible for processing A2A requests received by the transport.
 */
@OptIn(InternalA2AApi::class)
public class HttpJSONRPCServerTransport(
    override val requestHandler: RequestHandler,
) : JSONRPCServerTransport() {

    /**
     * Current running server instance if this transport is used as a standalone server.
     */
    private var server: EmbeddedServer<*, *>? = null
    private var serverMutex = Mutex()

    /**
     * Starts Ktor embedded server with Netty engine to handle A2A JSON-RPC requests, using the specified port and endpoint path.
     * Can be used to start a standalone server for quick prototyping or when no integration into the existing server is required.
     * The routing consists only of [transportRoutes].
     *
     * Can also optionally serve [AgentCard] at the specified [agentCardPath].
     *
     * If you need to integrate A2A request handling logic into existing Ktor application,
     * use [transportRoutes] method to mount the transport routes into existing [Route] configuration block.
     *
     * @param engineFactory An application engine factory.
     * @param port A port on which the server will listen.
     * @param path A JSON-RPC endpoint path to handle incoming requests.
     * @param wait If true, the server will block until it is stopped. Defaults to false.
     * @param agentCard An optional [AgentCard] that will be served at the specified [agentCardPath].
     * @param agentCardPath The path at which the [agentCard] will be served, if specified.
     * Defaults to [A2AConsts.AGENT_CARD_WELL_KNOWN_PATH].
     *
     * @throws IllegalStateException if the server is already running.
     *
     * @see [transportRoutes]
     */
    public suspend fun <TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration> start(
        engineFactory: ApplicationEngineFactory<TEngine, TConfiguration>,
        port: Int,
        path: String,
        wait: Boolean = false,
        agentCard: AgentCard? = null,
        agentCardPath: String = A2AConsts.AGENT_CARD_WELL_KNOWN_PATH,
    ): Unit = serverMutex.withLock {
        check(server == null) { "Server is already configured and running. Stop it before starting a new one." }

        server = embeddedServer(engineFactory, port) {
            install(SSE)

            routing {
                install(ContentNegotiation) {
                    json(JSONRPCJson)
                }

                transportRoutes(this, path)

                if (agentCard != null) {
                    get(agentCardPath) {
                        call.respond(agentCard)
                    }
                }
            }
        }.startSuspend(wait = wait)
    }

    /**
     * Stops the server gracefully within the specified time limits.
     *
     * @param gracePeriodMillis The time in milliseconds to allow ongoing requests to finish gracefully before shutting down.
     * @param timeoutMillis The maximum time in milliseconds to wait for the server to stop.
     *
     * @throws IllegalStateException if the server is not configured or running.
     */
    public suspend fun stop(gracePeriodMillis: Long = 1000, timeoutMillis: Long = 2000): Unit = serverMutex.withLock {
        check(server != null) { "Server is not configured or running." }

        server?.stopSuspend(gracePeriodMillis, timeoutMillis)
        server = null
    }

    /**
     * Routes for handling JSON-RPC HTTP requests.
     * Follows A2A specification in error handling.
     * Allows mounting A2A requests handling into an existing Ktor server application.
     * This can also be used to mount multiple A2A server transports on the same server, to serve multiple A2A agents.
     *
     * Example usage:
     * ```kotlin
     * embeddedServer(Netty, port = 8080) {
     *     install(SSE)
     *
     *     // Other configurations...
     *
     *     routing {
     *         // Other routes...
     *
     *         route("/a2a") {
     *             agentOneTransport.transportRoutes(this, "/agent-1")
     *             agentTwoTransport.transportRoutes(this, "/agent-2")
     *         }
     *     }
     * }.startSuspend(wait = true)
     * ```
     *
     * @param route The base route to which the transport routes should be mounted.
     * @param path JSON-RPC endpoint path that will be mounted under the base [route].
     */
    public fun transportRoutes(route: Route, path: String): Route = route.route(path) {
        plugin(SSE)

        install(ContentNegotiation) {
            json(JSONRPCJson)
        }

        // Handle incoming JSON-RPC requests, both regular and streaming
        post {
            runCatchingCancellable {
                val (request, a2aMethod) = parseJSONRPCRequest(call.receiveText())
                val ctx = call.toServerCallContext()

                runCatchingCancellable {
                    if (a2aMethod.streaming) {
                        handleRequestStreaming(request, ctx)
                    } else {
                        handleRequest(request, ctx)
                    }
                }.getOrElse {
                    call.respond(it.toJSONRPCErrorResponse(request.id))
                }
            }.getOrElse {
                call.respond(it.toJSONRPCErrorResponse())
            }
        }
    }

    /**
     * Handling A2A requests to regular methods.
     */
    private suspend fun RoutingContext.handleRequest(request: JSONRPCRequest, ctx: ServerCallContext) {
        val response = runCatchingCancellable {
            onRequest(
                request = request,
                ctx = ctx
            )
        }.getOrElse { it.toJSONRPCErrorResponse() }

        call.respond(response)
    }

    /**
     * Handling A2A requests to streaming methods.
     */
    private suspend fun RoutingContext.handleRequestStreaming(request: JSONRPCRequest, ctx: ServerCallContext) {
        val handle: suspend ServerSSESession.() -> Unit = {
            runCatchingCancellable {
                onRequestStreaming(
                    request = request,
                    ctx = ctx
                ).collect { response ->
                    send(
                        ServerSentEvent(JSONRPCJson.encodeToString(response))
                    )
                }
            }.getOrElse {
                send(
                    ServerSentEvent(
                        JSONRPCJson.encodeToString(it.toJSONRPCErrorResponse())
                    )
                )
            }
        }

        // Reply with SSE (implementation copied from SSE plugin code)
        call.response.apply {
            header(HttpHeaders.ContentType, ContentType.Text.EventStream.toString())
            header(HttpHeaders.CacheControl, "no-store")
            header(HttpHeaders.Connection, "keep-alive")
            header("X-Accel-Buffering", "no")
        }

        call.respond(SSEServerContent(call, handle))
    }

    private fun ApplicationCall.toServerCallContext(): ServerCallContext {
        return ServerCallContext(
            headers = request.headers.toMap()
        )
    }
}
