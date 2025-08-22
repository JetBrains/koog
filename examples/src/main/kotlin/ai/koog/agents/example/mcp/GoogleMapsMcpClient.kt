package ai.koog.agents.example.mcp

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.runBlocking

/**
 * Example of using the MCP (Model Context Protocol) integration with Google Maps.
 *
 * This example demonstrates how to:
 * 1. Start a Docker container with the Google Maps MCP server
 * 2. Connect to the MCP server using the McpToolRegistryProvider
 * 3. Create a ToolRegistry with tools from the MCP server
 * 4. Use the tools in an AI agent to answer a question about geographic data
 *
 * The example specifically shows how to get the elevation of the JetBrains office in Munich
 * by using the maps_geocode and maps_elevation tools provided by the MCP server.
 */
fun main() {
    // Get the API key from environment variables
    val googleMapsApiKey =
        System.getenv("GOOGLE_MAPS_API_KEY") ?: error("GOOGLE_MAPS_API_KEY environment variable not set")
    val openAIApiToken = System.getenv("OPENAI_API_KEY") ?: error("OPENAI_API_KEY environment variable not set")

    // Start the Docker container with the Google Maps MCP server
    val process = ProcessBuilder(
        "docker",
        "run",
        "-i",
        "-e",
        "GOOGLE_MAPS_API_KEY=$googleMapsApiKey",
        "mcp/google-maps"
    ).start()

    // Wait for the server to start
    Thread.sleep(2000)

    try {
        runBlocking {
            // Create the ToolRegistry with tools from the MCP server
            val toolRegistry = McpToolRegistryProvider.fromTransport(
                transport = McpToolRegistryProvider.defaultStdioTransport(process)
            )

            toolRegistry.tools.forEach {
                println(it.name)
                println(it.descriptor)
            }

            // Create the runner
            val agent = AIAgent(
                executor = simpleOpenAIExecutor(openAIApiToken),
                llmModel = OpenAIModels.Chat.GPT4o,
                toolRegistry = toolRegistry,
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
