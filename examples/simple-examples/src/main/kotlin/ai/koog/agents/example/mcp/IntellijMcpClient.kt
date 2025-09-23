package ai.koog.agents.example.mcp

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.runBlocking

/**
 * Example of using the MCP (Model Context Protocol) integration with IntelliJ MCP Server.
 *
 * This example demonstrates how to:
 * 1. Start an IntelliJ MCP server on port 64342
 * 2. Connect to the MCP server using the McpToolRegistryProvider's SSE client
 * 3. Create a ToolRegistry with tools from the MCP server
 * 4. Use the tools in an AI agent to automate IDE interactions
 *
 */
fun main() {
    val openAIApiToken = System.getenv("OPENAI_API_KEY") ?: error("OPENAI_API_KEY environment variable not set")

    // Start the Docker container with the Google Maps MCP server
    val process = ProcessBuilder(
        "npx",
        "-y",
        "@jetbrains/mcp-proxy"
    ).start()

    // Wait for the server to start
    Thread.sleep(2000)

    try {
        runBlocking {
            // Create the ToolRegistry with tools from the MCP server
            val toolRegistry = McpToolRegistryProvider.fromTransport(
                transport = McpToolRegistryProvider.defaultSseTransport("http://localhost:64342/sse")
            )

            toolRegistry.tools.forEach {
                println(it.name)
                println(it.descriptor)
            }

            // Create the runner
            val agent = AIAgent(
                promptExecutor = simpleOpenAIExecutor(openAIApiToken),
                llmModel = OpenAIModels.Chat.GPT4o,
                toolRegistry = toolRegistry,
            )
            val request = "Count number of files in koog project"
            println(request)
            agent.run(request + "You can only call tools. Get it by calling intellij tools.")
        }
    } finally {
        // Shutdown the Docker container
        process.destroy()
    }
}
