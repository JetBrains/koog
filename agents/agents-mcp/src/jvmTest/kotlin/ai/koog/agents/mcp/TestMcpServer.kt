package ai.koog.agents.mcp

import ai.koog.utils.io.SuitableForIO
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * A simple MCP server for testing purposes.
 * This server provides a simple tool that returns a greeting message.
 */
class TestMcpServer(private val port: Int) {
    private var serverJob: Job? = null
    private var isRunning = false

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
            inputSchema = Tool.Input(
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
            val name = request.arguments["name"]?.jsonPrimitive?.content
            val title = request.arguments["title"]?.jsonPrimitive?.content
            CallToolResult(
                content = listOf(TextContent("Hello, ${if (title.isNullOrEmpty()) "" else "$title "}$name!"))
            )
        }

        return server
    }

    /**
     * Starts the MCP server on the specified port.
     */
    fun start() {
        if (isRunning) return

        serverJob = CoroutineScope(Dispatchers.SuitableForIO).launch {
            embeddedServer(CIO, host = "0.0.0.0", port = port) {
                mcp {
                    return@mcp configureServer()
                }
            }.start(wait = true)
        }

        isRunning = true
        println("Test MCP server started on port $port")
    }

    /**
     * Stops the MCP server.
     */
    fun stop() {
        if (!isRunning) return

        serverJob?.cancel()
        serverJob = null
        isRunning = false
        println("Test MCP server stopped")
    }
}
