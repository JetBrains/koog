package ai.grazie.agents.mcp

import ai.grazie.code.agents.core.event.EventHandler
import ai.grazie.code.agents.local.simpleApi.simpleSingleRunAgent
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAIModels
import ai.jetbrains.code.prompt.executor.llms.all.simpleOpenAIExecutor
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered

/**
 * Example of using MCPToolRegistry.
 * This example starts a Docker container with the Google Maps MCP server,
 * creates an MCPToolRegistry that connects to the server,
 * and creates a ToolRegistry with tools from the MCP server.
 */
fun main() {
    // Get the API key from environment variables
    val apiKey = System.getenv("GOOGLE_MAPS_API_KEY")

    // Start the Docker container with the Google Maps MCP server
    val process = ProcessBuilder(
        "docker", "run", "-i",
        "-e", "GOOGLE_MAPS_API_KEY=$apiKey",
        "mcp/google-maps"
    ).start()

    // Wait for the server to start
    Thread.sleep(2000)

    try {
        // Create the MCP client
        val mcp = Client(clientInfo = Implementation(name = "mcp-client-cli", version = "1.0.0"))

        // Setup I/O transport using the process streams
        val transport = StdioClientTransport(
            input = process.inputStream.asSource().buffered(),
            output = process.outputStream.asSink().buffered()
        )

        runBlocking {
            // Connect the MCP client to the server using the transport
            mcp.connect(transport)

            // Create the ToolRegistry with tools from the MCP server
            val toolRegistry = MCPToolRegistryProvider().fromClient(mcp)

            // Create the runner
            val agent = simpleSingleRunAgent(
                executor = simpleOpenAIExecutor(System.getenv("OPEN_AI_TOKEN")),
                llmModel = OpenAIModels.GPT4o,
                toolRegistry = toolRegistry,
                eventHandler = EventHandler {
                    onToolCall { stage, tool, args ->
                        println("Tool called: stage ${stage.name}, tool ${tool.name}, args $args")
                    }

                    handleError {
                        println("An error occurred: ${it.message}\n${it.stackTraceToString()}")
                        true
                    }

                    handleResult {
                        println("Result: $it")
                    }
                },
                cs = this
            )
            agent.run(
                "Get coordinates of Jetbrains Office in Berlin." +
                        "You can only call tools. Get it by calling maps_geocode tool."
            )
        }
    } finally {
        // Shutdown the Docker container
        process.destroy()
    }
}
