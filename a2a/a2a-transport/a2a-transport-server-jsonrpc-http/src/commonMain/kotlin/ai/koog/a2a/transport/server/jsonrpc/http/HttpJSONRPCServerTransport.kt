package ai.koog.a2a.transport.server.jsonrpc.http

import ai.koog.a2a.annotations.InternalA2AApi
import ai.koog.a2a.consts.A2AConsts
import ai.koog.a2a.exceptions.A2AInvalidRequestException
import ai.koog.a2a.exceptions.A2AParseException
import ai.koog.a2a.model.AgentCard
import ai.koog.a2a.transport.RequestHandler
import ai.koog.a2a.transport.ServerCallContext
import ai.koog.a2a.transport.jsonrpc.JSONRPCServerTransport
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCJson
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCRequest
import ai.koog.a2a.utils.runCatchingCancellable
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.application.pluginOrNull
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.ApplicationEngineFactory
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.send
import io.ktor.server.sse.sse
import io.ktor.util.toMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.serializer

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
        agentCard: AgentCard? = null,
        agentCardPath: String = A2AConsts.AGENT_CARD_WELL_KNOWN_PATH,
    ): Unit = serverMutex.withLock {
        check(server == null) { "Server is already configured and running. Stop it before starting a new one." }

        server = embeddedServer(engineFactory, port) {
            install(SSE)

            routing {
                transportRoutes(this, path)

                if (agentCard != null) {
                    get(agentCardPath) {
                        call.respond(agentCard)
                    }
                }
            }
        }.startSuspend(wait = false)
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
    @OptIn(InternalA2AApi::class)
    public fun transportRoutes(route: Route, path: String): Route = route.route(path) {
        if (application.pluginOrNull(SSE) == null) {
            throw IllegalStateException("SSE plugin must be installed in the application to add these routes.")
        }

        install(ContentNegotiation) {
            json(JSONRPCJson)
        }

        // Regular JSON-RPC requests
        post {
            val response = runCatchingCancellable {
                onRequest(
                    request = call.receiveJSONRPCRequest(),
                    ctx = call.toServerCallContext()
                )
            }.getOrElse { it.toJSONRPCErrorResponse() }

            call.respond(response)
        }

        // Streaming JSON-RPC requests
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
                ).collect { response ->
                    send(response)
                }
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
            val rawBody = receiveText()
            JSONRPCJson.parseToJsonElement(rawBody)
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
