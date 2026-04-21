package ai.koog.http.client.test

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.SSEServerContent
import io.ktor.server.sse.ServerSSESession
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * MockWebServer provides a simple test server using Ktor with CIO engine.
 * This server can be used to test HTTP clients with both regular POST requests and SSE streaming.
 */
@OptIn(ExperimentalAtomicApi::class)
class MockWebServer {
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private val portNumber = AtomicInt(0)

    /**
     * Configuration for a mock get endpoint
     */
    data class GetEndpointConfig(
        val path: String,
        val responseBody: String,
        val statusCode: HttpStatusCode = HttpStatusCode.OK,
        val contentType: ContentType = ContentType.Text.Plain,
        val expectedParameters: Map<String, String> = emptyMap()
    )

    /**
     * Configuration for a mock post endpoint
     */
    data class PostEndpointConfig(
        val path: String,
        val responseBody: String,
        val statusCode: HttpStatusCode = HttpStatusCode.OK,
        val contentType: ContentType = ContentType.Text.Plain,
        val expectedParameters: Map<String, String> = emptyMap()
    )

    /**
     * Configuration for a mock SSE endpoint
     */
    data class SSEEndpointConfig(
        val path: String,
        val events: List<String>
    )

    /**
     * Starts the server with the specified endpoint configurations
     */
    fun start(
        getEndpoints: List<GetEndpointConfig> = emptyList(),
        postEndpoints: List<PostEndpointConfig> = emptyList(),
        sseEndpoints: List<SSEEndpointConfig> = emptyList(),
        port: Int = 0
    ) {
        require(
            !(postEndpoints.isNotEmpty() && sseEndpoints.isNotEmpty())
        ) { "Cannot specify both regular and SSE endpoints" }

        server = embeddedServer(CIO, port = port) {
            if (sseEndpoints.isNotEmpty()) {
                install(SSE)
            }

            routing {
                // Configure regular GET endpoints
                getEndpoints.forEach { config ->
                    get(config.path) {
                        val actualParameters = call.request.queryParameters

                        // Validate expected parameters if specified
                        config.expectedParameters.forEach { (key, expectedValue) ->
                            val actualValue = actualParameters[key]
                            if (actualValue != expectedValue) {
                                call.respondText(
                                    text = "Parameter mismatch: expected $key=$expectedValue, got $key=$actualValue",
                                    status = HttpStatusCode.BadRequest
                                )
                                return@get
                            }
                        }

                        call.respondText(
                            text = config.responseBody,
                            contentType = config.contentType,
                            status = config.statusCode
                        )
                    }
                }

                // Configure regular POST endpoints
                postEndpoints.forEach { config ->
                    post(config.path) {
                        call.receiveText()
                        val actualParameters = call.request.queryParameters

                        // Validate expected parameters if specified
                        config.expectedParameters.forEach { (key, expectedValue) ->
                            val actualValue = actualParameters[key]
                            if (actualValue != expectedValue) {
                                call.respondText(
                                    text = "Parameter mismatch: expected $key=$expectedValue, got $key=$actualValue",
                                    status = HttpStatusCode.BadRequest
                                )
                                return@post
                            }
                        }

                        call.respondText(
                            text = config.responseBody,
                            contentType = config.contentType,
                            status = config.statusCode
                        )
                    }
                }

                // Configure SSE endpoints for POST requests (mimicking a2a-transport pattern)
                sseEndpoints.forEach { config ->
                    post(config.path) {
                        call.receiveText()

                        val handle: suspend ServerSSESession.() -> Unit = {
                            config.events.forEach { event ->
                                send(event)
                                delay(10)
                            }
                        }

                        call.response.apply {
                            header(HttpHeaders.ContentType, ContentType.Text.EventStream.toString())
                            header(HttpHeaders.CacheControl, "no-store")
                            header(HttpHeaders.Connection, "keep-alive")
                        }

                        call.respond(SSEServerContent(call, handle))
                    }
                }
            }
        }.start(wait = false)

        // Store the actual port for reference
        runBlocking {
            val actualPort = server?.engine?.resolvedConnectors()?.first()?.port ?: 0
            portNumber.store(actualPort)
        }
    }

    /**
     * Stops the server
     */
    fun stop() {
        server?.stop(gracePeriodMillis = 100, timeoutMillis = 1000)
        server = null
    }

    /**
     * Returns the base URL of the running server
     */
    fun url(path: String = ""): String {
        require(port > 0) { "Server is not started" }
        return "http://127.0.0.1:$port$path"
    }

    /**
     * Returns the port the server is running on
     */
    val port: Int
        get() = portNumber.load()
}
