package ai.koog.agents.mcp

import ai.koog.utils.io.SuitableForIO
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Transport mode for the test MCP server.
 */
enum class TestTransportMode {
    SSE,
    StreamableHttp,
}

/**
 * A simple MCP server for testing purposes.
 * This server provides a simple tool that returns a greeting message.
 *
 * Pass [port] = 0 (default) to let the OS allocate a free port; read [resolvedPort] after [start].
 */
class TestMcpServer(
    private val port: Int = 0,
    private val transportMode: TestTransportMode = TestTransportMode.SSE,
) {
    private var serverJob: Job? = null

    @Volatile
    private var embeddedServer: EmbeddedServer<*, *>? = null

    @Volatile
    private var isRunning = false

    /** The actual port the server is listening on. Valid after [start] returns. */
    var resolvedPort: Int = 0
        private set

    /**
     * Configures the MCP server with a simple greeting tool.
     */
    private fun configureServer(): Server {
        val server = Server(
            Implementation(
                name = "test-mcp-server",
                version = "0.1.0"
            ),
            ServerOptions(
                capabilities = ServerCapabilities(
                    prompts = ServerCapabilities.Prompts(listChanged = true),
                    resources = ServerCapabilities.Resources(subscribe = true, listChanged = true),
                    tools = ServerCapabilities.Tools(listChanged = true),
                )
            )
        )

        // Add a simple greeting tool
        server.addTool(
            name = "greeting",
            description = "A simple greeting tool",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("name") {
                        put("type", "string")
                        put("description", "A name to greet")
                    }
                    putJsonObject("title") {
                        putJsonArray("anyOf") {
                            addJsonObject {
                                put("type", "null")
                            }
                            addJsonObject {
                                put("type", "string")
                            }
                        }
                        put("description", "Title to use in the greeting")
                    }
                },
                required = listOf("name")
            )
        ) { request ->
            val name = request.arguments?.get("name")?.jsonPrimitive?.content
            val title = request.arguments?.get("title")?.jsonPrimitive?.content
            CallToolResult(
                content = listOf(TextContent("Hello, ${if (title.isNullOrEmpty()) "" else "$title "}$name!"))
            )
        }

        // A completely empty tool that accepts nothing and returns nothing
        server.addTool(
            name = "empty",
            description = "An empty tool",
            inputSchema = ToolSchema()
        ) {
            CallToolResult(content = emptyList())
        }

        return server
    }

    /**
     * Starts the MCP server and blocks until it is listening.
     */
    fun start() = runBlocking {
        if (isRunning) return@runBlocking

        val ready = CompletableDeferred<Int>()

        serverJob = CoroutineScope(Dispatchers.SuitableForIO).launch {
            try {
                val emb = embeddedServer(CIO, host = "0.0.0.0", port = port) {
                    when (transportMode) {
                        TestTransportMode.SSE -> mcp { configureServer() }
                        TestTransportMode.StreamableHttp -> mcpStreamableHttp { configureServer() }
                    }
                }
                embeddedServer = emb

                // Signal readiness once CIO has bound the listening socket. Runs concurrently
                // with emb.start(wait = true) below, which blocks until the engine stops.
                launch {
                    while (isActive) {
                        val connectors = emb.engine.resolvedConnectors()
                        if (connectors.isNotEmpty()) {
                            ready.complete(connectors.first().port)
                            return@launch
                        }
                        delay(50.milliseconds)
                    }
                }

                emb.start(wait = true)
                isRunning = false
            } catch (t: Throwable) {
                // Surface startup failure to start() instead of leaving it to time out.
                ready.completeExceptionally(t)
                throw t
            }
        }

        resolvedPort = withTimeout(10.seconds) { ready.await() }
        isRunning = true
        println("Test MCP server started on port $resolvedPort (transport: $transportMode)")
    }

    /**
     * Stops the MCP server.
     */
    fun stop() {
        if (!isRunning) return

        embeddedServer?.stop(gracePeriodMillis = 1000, timeoutMillis = 1000)
        serverJob?.cancel()
        serverJob = null
        embeddedServer = null
        isRunning = false
        println("Test MCP server stopped")
    }
}
