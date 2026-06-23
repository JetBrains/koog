package ai.koog.a2a.transport.server.jsonrpc.http

import ai.koog.a2a.annotations.InternalA2AApi
import ai.koog.a2a.consts.A2APaths
import ai.koog.a2a.model.AgentCard
import ai.koog.a2a.transport.RequestHandler
import ai.koog.a2a.transport.jsonrpc.JSONRPCServerTransport
import ai.koog.a2a.transport.jsonrpc.serialization.JSONRPCJson
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.ApplicationEngineFactory
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Standalone Ktor server implementing A2A JSON-RPC server transport.
 * This transport is intended for serving a single agent as a standalone server, or for quick prototyping.
 *
 * To integrate an agent or multiple agents into an existing Ktor server,
 * use [a2aJsonRpcTransportRoute] and [a2aAgentCardRoute]
 *
 * Example usage :
 * ```kotlin
 * val transport = HttpJSONRPCServerTransport(A2AServer(...))
 *
 * transport.start(Netty, 8080, "/my-agent", agentCard = AgentCard(...), agentCardPath = "/my-agent-card.json")
 * transport.stop()
 * ```
 *
 * @property requestHandler The handler responsible for processing A2A requests received by the transport,
 * usually an instance of [ai.koog.a2a.server.A2AServer] or its custom implementation.
 */
@OptIn(InternalA2AApi::class)
public class HttpJSONRPCServerTransport(
    requestHandler: RequestHandler,
) : JSONRPCServerTransport(requestHandler) {

    /**
     * Current running server instance if this transport is used as a standalone server.
     */
    private var server: EmbeddedServer<*, *>? = null
    private var serverMutex = Mutex()

    /**
     * Starts Ktor embedded server with Netty engine to handle A2A JSON-RPC requests, using the specified port and endpoint path.
     * Can be used to start a standalone server for quick prototyping or when no integration into the existing server is required.
     *
     * Can also optionally serve [AgentCard] at the specified [agentCardPath].
     *
     * @param engineFactory An application engine factory.
     * @param port A port on which the server will listen.
     * @param path A JSON-RPC endpoint path to handle incoming requests.
     * @param wait If true, the server will block until it is stopped. Defaults to false.
     * @param agentCard An optional [AgentCard] that will be served at the specified [agentCardPath].
     * @param agentCardPath The path at which the [agentCard] will be served, if specified.
     * Defaults to [A2APaths.AGENT_CARD_WELL_KNOWN_PATH].
     *
     * @throws IllegalStateException if the server is already running.
     */
    public suspend fun <TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration> start(
        engineFactory: ApplicationEngineFactory<TEngine, TConfiguration>,
        port: Int,
        path: String,
        wait: Boolean = false,
        agentCard: AgentCard? = null,
        agentCardPath: String = A2APaths.AGENT_CARD_WELL_KNOWN_PATH,
    ): Unit = serverMutex.withLock {
        check(server == null) { "Server is already configured and running. Stop it before starting a new one." }

        server = embeddedServer(engineFactory, port) {
            install(SSE)

            routing {
                install(ContentNegotiation) {
                    json(JSONRPCJson)
                }

                install(CORS) {
                    anyHost()
                    allowNonSimpleContentTypes = true
                }

                a2aJsonRpcTransportRoute(path, this@HttpJSONRPCServerTransport, )

                if (agentCard != null) {
                    a2aAgentCardRoute(agentCardPath, agentCard)
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
}
