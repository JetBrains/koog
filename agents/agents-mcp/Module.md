# Module agents-mcp

A module provides integration with [Model Context Protocol (MCP)](https://modelcontextprotocol.io) servers.
The main components of the MCP integration in Koog are:
- [**McpToolRegistryProvider**](src/jvmMain/kotlin/ai/koog/agents/mcp/McpToolRegistryProvider.kt): Creates tool registries that connect to MCP servers
- [**McpTool**](src/jvmMain/kotlin/ai/koog/agents/mcp/McpTool.kt): A bridge between the Koog agent framework's Tool interface and the MCP SDK
- [**McpToolDescriptorParser**](src/jvmMain/kotlin/ai/koog/agents/mcp/McpToolDefinitionParser.kt): Parses tool definitions from the MCP SDK to the Koog tool descriptor format


## Overview

### What is MCP?

The Model Context Protocol (MCP) is a standardized protocol that enables AI agents to interact with external tools and services through a consistent interface.
MCP works by exposing tools and prompts as API endpoints that can be called by AI agents.
Each tool has a defined name and input schema that describes its inputs and outputs in JSON SHEMA format.
To read more about MCP visit [https://modelcontextprotocol.io](https://modelcontextprotocol.io)

### How to use MCP servers?
You can find ready-to-use mcp servers in the [MCP Marketplace](https://mcp.so/) or [MCP DockerHub](https://hub.docker.com/u/mcp).
MCP servers support stdio transport and optionally sse transport protocols to communicate with the agent.

### How MCP is integrated with Koog?

The Koog framework integrates with MCP using the [MCP SDK](https://github.com/modelcontextprotocol/kotlin-sdk) with the additional api extensions presented in module `agent-mcp`.
This integration allows Koog agents to:

1. Connect to MCP servers through various transport mechanisms (stdio, SSE)
2. Retrieve available tools from the MCP server
3. Transform MCP tools into the Koog agent framework's Tool interface
4. Register the transformed tools in a ToolRegistry
5. Call MCP tools with arguments provided by the LLM

### How to Use MCP with Koog?

#### Setting Up an MCP Connection

To use MCP with Koog, you need to:

1. Start an MCP server (either as a process, Docker container, or web service)
2. Create a transport to communicate with the server
3. Create a ToolRegistry with tools from the MCP server
4. Use the tools in an AI agent

Here's a basic example of setting up an MCP connection:

```kotlin
// Start the MCP server (e.g., as a process)
val process = ProcessBuilder("path/to/mcp/server").start()

// Create a ToolRegistry with tools from the MCP server
val toolRegistry = McpToolRegistryProvider.fromTransport(
    transport = McpToolRegistryProvider.defaultStdioTransport(process)
)

// Use the tools in an AI agent
val agent = AIAgent(
    promptExecutor = executor,
    strategy = strategy,
    agentConfig = agentConfig,
    toolRegistry = toolRegistry
)

// Run the agent
agent.run("Your task here")
```

#### Transport Types

MCP supports different transport mechanisms for communication:

##### Standard Input/Output (stdio)

Use stdio transport when the MCP server is running as a separate process:

```kotlin
val process = ProcessBuilder("path/to/mcp/server").start()
val transport = McpToolRegistryProvider.defaultStdioTransport(process)
```

##### Server-Sent Events (SSE)

Use SSE transport when the MCP server is running as a web service:

```kotlin
val transport = McpToolRegistryProvider.defaultSseTransport("http://localhost:8931")
```

### Examples

#### Google Maps MCP Integration

This example demonstrates using MCP to connect to a [Google Maps](https://mcp.so/server/google-maps/modelcontextprotocol) server for geographic data:

```kotlin
// Start the Docker container with the Google Maps MCP server
val process = ProcessBuilder(
    "docker", "run", "-i",
    "-e", "GOOGLE_MAPS_API_KEY=$googleMapsApiKey",
    "mcp/google-maps"
).start()

// Create the ToolRegistry with tools from the MCP server
val toolRegistry = McpToolRegistryProvider.fromTransport(
    transport = McpToolRegistryProvider.defaultStdioTransport(process)
)

// Create and run the agent
val agent = simpleSingleRunAgent(
    executor = simpleOpenAIExecutor(openAIApiToken),
    llmModel = OpenAIModels.Chat.GPT4o,
    toolRegistry = toolRegistry,
)
agent.run("Get elevation of the Jetbrains Office in Munich, Germany?")
```

#### Playwright MCP Integration

This example demonstrates using MCP to connect to a [Playwright](https://mcp.so/server/playwright-mcp/microsoft) server for web automation:

```kotlin
// Start the Playwright MCP server
val process = ProcessBuilder(
    "npx", "@playwright/mcp@latest", "--port", "8931"
).start()

// Create the ToolRegistry with tools from the MCP server
val toolRegistry = McpToolRegistryProvider.fromTransport(
    transport = McpToolRegistryProvider.defaultSseTransport("http://localhost:8931")
)

// Create and run the agent
val agent = simpleSingleRunAgent(
    executor = simpleOpenAIExecutor(openAIApiToken),
    llmModel = OpenAIModels.Chat.GPT4o,
    toolRegistry = toolRegistry,
)
agent.run("Open a browser, navigate to jetbrains.com, accept all cookies, click AI in toolbar")
```
