package ai.koog.a2a.transport.server.jsonrpc.http

import ai.koog.a2a.transport.RequestHandler
import ai.koog.a2a.transport.ServerTransport
import io.ktor.server.application.install
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.ApplicationEngineFactory
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.milliseconds

public class HttpJSONRPCServer<E : ApplicationEngine, C : ApplicationEngine.Configuration>(
    factory: ApplicationEngineFactory<E, C>,
    host: String = "0.0.0.0",
    port: Int = 0,
    path: String = "/a2a",
    handler: RequestHandler,
    private val serverTransport: HttpJSONRPCServerTransport = HttpJSONRPCServerTransport(handler),
) : ServerTransport by serverTransport {

    private val server = embeddedServer(factory, host = host, port = port) {
        install(SSE)
        routing {
            serverTransport.transportRoutesInternal(route = this, path = path)
        }
    }

    public fun start() {
        server.start(wait = false)
    }

    /**
     * Stops the server.
     */
    public fun stop() {
        server.stop(1000, 1000)
    }

    /**
     * Represents the port on which the server is currently listening for incoming connections.
     *
     * This property is lazily initialized and retrieves the port number by accessing
     * the first resolved connector of the server's application engine. The value is determined
     * once the server is started, and any subsequent access will return the same value.
     *
     * Accessing this property before the server is fully started may result in a runtime exception.
     */
    public val port: Int by lazy {
        runBlocking {
            server.engine.waitForPort()
        }
    }

    private tailrec suspend fun ApplicationEngine.waitForPort(): Int {
        val connectors = resolvedConnectors()
        return if (connectors.isNotEmpty()) {
            connectors.first().port
        } else {
            delay(50.milliseconds)
            waitForPort()
        }
    }
}


