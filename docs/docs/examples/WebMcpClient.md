# Web Scraping with The Web MCP by Bright Data and Koog

[:material-github: Open on GitHub](https://github.com/JetBrains/koog/blob/develop/examples/bright-data-mcp/){ .md-button .md-button--primary }
[:material-download: Download .kt](https://raw.githubusercontent.com/JetBrains/koog/develop/examples/bright-data-mcp/Main.kt){ .md-button }

In this tutorial, you'll connect a Koog agent to Bright Data's Web MCP server and let it perform web scraping and data collection tasks. We'll demonstrate how to search for information about Koog.ai using Bright Data's powerful web scraping infrastructure through the Model Context Protocol.

We'll keep things simple and reproducible, focusing on a minimal but realistic agent + tools setup you can adapt for your own web scraping needs.

## Prerequisites

- An OpenAI API key exported as an environment variable: `OPENAI_API_KEY`
- A Bright Data API token exported as an environment variable: `BRIGHT_DATA_API_TOKEN`
- Node.js and npx available on your PATH
- Kotlin development environment with Koog dependencies

**Tip**: The Bright Data MCP server provides access to enterprise-grade web scraping tools that can handle complex websites, CAPTCHAs, and anti-bot measures.

## 1) Set up your API credentials

We read both API keys from environment variables to keep secrets secure and out of your code.

```kotlin
// Get API keys from environment variables
val openAIApiKey = System.getenv("OPENAI_API_KEY")
    ?: error("OPENAI_API_KEY environment variable is not set")
val brightDataToken = System.getenv("BRIGHT_DATA_API_TOKEN")
    ?: error("BRIGHT_DATA_API_TOKEN environment variable is not set")
```

## 2) Start The Web MCP server by Bright Data

We'll launch Bright Data's MCP server using `npx` and configure it with your API token. The server will expose web scraping capabilities through the Model Context Protocol.

```kotlin
println("Starting Bright Data MCP server...")

// Start the Bright Data MCP server as a separate process
val processBuilder = ProcessBuilder("npx", "@brightdata/mcp")

// Set the API_TOKEN environment variable for the MCP server process
val environment = processBuilder.environment()
environment["API_TOKEN"] = brightDataToken

// Start the process
val process = processBuilder.start()

// Give the process a moment to start
Thread.sleep(2000)
```

## 3) Connect from Koog and create the agent

We build a Koog `AIAgent` with an OpenAI executor and connect its tool registry to the Bright Data MCP server via STDIO transport. Then we'll explore the available tools and run a web scraping task.

```kotlin
println("Creating STDIO transport...")
try {
    // Create the STDIO transport
    val transport = McpToolRegistryProvider.defaultStdioTransport(process)
    
    println("Creating tool registry...")
    
    // Create a tool registry with tools from the Bright Data MCP server
    val toolRegistry = McpToolRegistryProvider.fromTransport(
        transport = transport,
        name = "bright-data-client",
        version = "1.0.0"
    )
    
    // Print available tools (optional - for debugging)
    println("Available tools from Bright Data MCP server:")
    toolRegistry.tools.forEach { tool ->
        println("- ${tool.name}")
    }
    
    // Create the agent with MCP tools
    val agent = AIAgent(
        executor = simpleOpenAIExecutor(openAIApiKey),
        systemPrompt = "You are a helpful assistant with access to web scraping and data collection tools from Bright Data. You can help users gather information from websites, analyze web data, and provide insights.",
        llmModel = OpenAIModels.Chat.GPT4o,
        temperature = 0.7,
        toolRegistry = toolRegistry,
        maxIterations = 100
    )
    
    val result = agent.run("Please search for Koog.ai and tell me what is it and who invented it")
    
    println("\nAgent response:")
    println(result)
    
} catch (e: Exception) {
    println("Error: ${e.message}")
    e.printStackTrace()
} finally {
    println("Shutting down MCP server...")
    process.destroyForcibly()
}
```

## 4) Complete code example

Here's the complete working example that demonstrates web scraping with The Web MCP by Bright Data:

```kotlin
package koog

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.runBlocking

/**
 * The entry point of the program demonstrating AI-driven web scraping and data collection.
 *
 * This function initializes a Bright Data MCP server, sets up tool integration,
 * and defines an AI agent for interacting with web scraping tools. It demonstrates the
 * following key operations:
 *
 * 1. Starts the Bright Data MCP server using a subprocess with proper API token configuration.
 * 2. Configures a registry of tools from the MCP server via STDIO transport communication.
 * 3. Creates an AI agent leveraging OpenAI's GPT-4o model with web scraping capabilities.
 * 4. Runs the agent to perform a specified task (e.g., searching for and analyzing web content
 *    about Koog.ai).
 * 5. Cleans up by shutting down the MCP server process after execution.
 *
 * This function is intended for tutorial purposes, demonstrating how to integrate
 * MCP (Model Context Protocol) servers with AI agents for web data collection and analysis.
 * It requires OPENAI_API_KEY and BRIGHT_DATA_API_TOKEN environment variables to be set.
 */
fun main() = runBlocking {
    // Get API keys from environment variables
    val openAIApiKey = System.getenv("OPENAI_API_KEY")
        ?: error("OPENAI_API_KEY environment variable is not set")
    val brightDataToken = System.getenv("BRIGHT_DATA_API_TOKEN")
        ?: error("BRIGHT_DATA_API_TOKEN environment variable is not set")

    println("Starting Bright Data MCP server...")

    // Start the Bright Data MCP server as a separate process
    val processBuilder = ProcessBuilder("npx", "@brightdata/mcp")

    // Set the API_TOKEN environment variable for the MCP server process
    val environment = processBuilder.environment()
    environment["API_TOKEN"] = brightDataToken

    // Start the process
    val process = processBuilder.start()

    // Give the process a moment to start
    Thread.sleep(2000)

    println("Creating STDIO transport...")

    try {
        // Create the STDIO transport
        val transport = McpToolRegistryProvider.defaultStdioTransport(process)
        
        println("Creating tool registry...")
        
        // Create a tool registry with tools from the Bright Data MCP server
        val toolRegistry = McpToolRegistryProvider.fromTransport(
            transport = transport,
            name = "bright-data-client",
            version = "1.0.0"
        )
        
        // Print available tools (optional - for debugging)
        println("Available tools from Bright Data MCP server:")
        toolRegistry.tools.forEach { tool ->
            println("- ${tool.name}")
        }
        
        // Create the agent with MCP tools
        val agent = AIAgent(
            executor = simpleOpenAIExecutor(openAIApiKey),
            systemPrompt = "You are a helpful assistant with access to web scraping and data collection tools from Bright Data. You can help users gather information from websites, analyze web data, and provide insights.",
            llmModel = OpenAIModels.Chat.GPT4o,
            temperature = 0.7,
            toolRegistry = toolRegistry,
            maxIterations = 100
        )
        
        val result = agent.run("Please search for Koog.ai and tell me what is it and who invented it")
        
        println("\nAgent response:")
        println(result)
        
    } catch (e: Exception) {
        println("Error: ${e.message}")
        e.printStackTrace()
    } finally {
        println("Shutting down MCP server...")
        process.destroyForcibly()
    }
}
```

## Troubleshooting

- **Connection issues**: If the agent can't connect to the MCP server, ensure the Bright Data MCP package is properly installed via `npx @brightdata/mcp`.
- **API token errors**: Double-check that your `BRIGHT_DATA_API_TOKEN` is valid and has the necessary permissions for web scraping.
- **OpenAI authentication**: Verify that your `OPENAI_API_KEY` environment variable is correctly set and the API key is valid.
- **Process timeout**: If the server takes longer to start, increase the `Thread.sleep(2000)` duration.

## Next steps

- **Explore different queries**: Try scraping different websites or searching for various topics.
- **Custom tool integration**: Add your own tools alongside Bright Data's web scraping capabilities.
- **Advanced scraping**: Leverage Bright Data's advanced features like residential proxies, CAPTCHA solving, and JavaScript rendering.
- **Data processing**: Combine the scraped data with other Koog agents for analysis and insights.
- **Production deployment**: Integrate this pattern into your applications for automated web data collection.

## What you've learned

This tutorial demonstrated how to:
- Set up and configure The Web MCP by Bright Data
- Connect a Koog AI agent to external MCP servers via STDIO transport
- Perform AI-driven web scraping tasks using natural language instructions
- Handle proper resource cleanup and error management
- Structure code for production-ready web scraping applications

The combination of Koog's AI agent capabilities with Bright Data's enterprise web scraping infrastructure provides a powerful foundation for automated data collection and analysis workflows.