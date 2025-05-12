package ai.grazie.code.agents.example.mcp

import ai.grazie.code.agents.core.api.simpleSingleRunAgent
import ai.grazie.code.agents.core.event.EventHandler
import ai.grazie.code.agents.mcp.McpToolRegistryProvider
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAIModels
import ai.jetbrains.code.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.runBlocking

/**
 * Example of using MCPToolRegistry with Playwright MCP server.
 * This example connects to a Playwright MCP server running on port 8931,
 * creates an MCPToolRegistry that connects to the server,
 * and creates a ToolRegistry with tools from the MCP server.
 *
 * This example uses curl to connect to the Playwright MCP server via HTTP.
 * The MCP SDK doesn't support HTTP/SSE directly, so we use curl as a bridge.
 */
fun main() {
    // Get the API key from environment variables
    val openAIApiToken = System.getenv("OPEN_AI_TOKEN")

    // Start the Docker container with the Google Maps MCP server
    val process = ProcessBuilder(
        "npx", "@playwright/mcp@latest", "--port", "8931"
    ).start()

    // Wait for the server to start
    Thread.sleep(2000)

    try {
        runBlocking {
            try {
                // Create the ToolRegistry with tools from the MCP server
                println("Connecting to Playwright MCP server...")
                val toolRegistry = McpToolRegistryProvider().fromSseClient("http://localhost:8931")
                println("Successfully connected to Playwright MCP server")

                // Create the runner
                val agent = simpleSingleRunAgent(
                    executor = simpleOpenAIExecutor(openAIApiToken),
                    llmModel = OpenAIModels.GPT4o,
                    toolRegistry = toolRegistry,
                    eventHandler = EventHandler {
                        onToolCall { stage, tool, args ->
                            println("Tool called: stage ${stage.name}, tool ${tool.name}, args $args")
                        }

                        handleError {
                            println("An error occurred during agent execution: ${it.message}\n${it.stackTraceToString()}")
                            true
                        }

                        handleResult {
                            println("Result: $it")
                        }
                    },
                    cs = this
                )
                val request = "Open a browser, navigate to jetbrains.com, and take a screenshot"
                println("Sending request: $request")
                agent.run(
                    request +
                            "You can only call tools. Use the Playwright tools to complete this task."
                )
            } catch (e: Exception) {
                println("Error connecting to Playwright MCP server: ${e.message}")
                e.printStackTrace()
            }
        }
    } finally {
        // Shutdown the curl process
        println("Closing connection to Playwright MCP server")
        process.destroy()
    }
}
