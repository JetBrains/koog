package ai.grazie.code.agents.example.mcp

import ai.grazie.code.agents.core.api.simpleSingleRunAgent
import ai.grazie.code.agents.core.event.EventHandler
import ai.grazie.code.agents.mcp.MCPToolRegistryProvider
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAIModels
import ai.jetbrains.code.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.runBlocking

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
        runBlocking {
            // Create the ToolRegistry with tools from the MCP server
            val toolRegistry = MCPToolRegistryProvider().fromProcess(process)

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
            val request = "Get elevation of the Jetbrains Office in Munich, Germany?"
            println(request)
            agent.run(
                request +
                        "You can only call tools. Get it by calling maps_geocode and maps_elevation tools."
            )
        }
    } finally {
        // Shutdown the Docker container
        process.destroy()
    }
}
