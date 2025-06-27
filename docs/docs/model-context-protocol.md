# Model Context Protocol

Model Context Protocol (MCP) is a standardized protocol that lets AI agents interact with external tools and services through a consistent interface.

MCP exposes tools and prompts as API endpoints that AI agents can call. Each tool has a specific name and an input schema that describes its inputs and outputs using the JSON Schema format.

The Koog framework provides integration with MCP servers, enabling you to incorporate MCP tools into your Koog agents.

To learn more about the protocol, see the [Model Context Protocol](https://modelcontextprotocol.io) documentation.

## MCP servers

MCP servers implement Model Context Protocol and provide a standardized way for AI agents to interact with tools and services.

You can find ready-to-use MCP servers in the [MCP Marketplace](https://mcp.so/) or [MCP DockerHub](https://hub.docker.com/u/mcp).

The MCP servers support the following transport protocols to communicate with agents:

* Standard input/output (stdio) transport protocol used to communicate with the MCP servers running as separate processes. For example, a Docker container or a CLI tool.
* Server-sent events (SSE) transport protocol (optional) used to communicate with the MCP servers over HTTP.

## Integration with Koog

The Koog framework integrates with MCP using the [MCP SDK](https://github.com/modelcontextprotocol/kotlin-sdk) with the additional API extensions presented in the `agent-mcp` module.

This integration lets the Koog agents perform the following:

* Connect to MCP servers through various transport mechanisms (stdio, SSE).
* Retrieve available tools from an MCP server.
* Transform MCP tools into the Koog tool interface.
* Register the transformed tools in a tool registry.
* Call MCP tools with arguments provided by the LLM.

### Key components

Here are the main components of the MCP integration in Koog:

| Component                                                                                                                                                           | Description                                                                                                |
|---------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------|
| [`McpTool`](https://api.koog.ai/agents/agents-mcp/ai.koog.agents.mcp/-mcp-tool/index.html)                                                                          | Serves as a bridge between the Koog tool interface and the MCP SDK.                  |                                                                              |
| [`McpToolDescriptorParser`](https://api.koog.ai/agents/agents-mcp/ai.koog.agents.mcp/-mcp-tool-descriptor-parser/index.html)                                        | Parses MCP tool definitions into the Koog tool descriptor format.                                          |
| [`McpToolRegistryProvider`](https://api.koog.ai/agents/agents-mcp/ai.koog.agents.mcp/-mcp-tool-registry-provider/index.html?query=object%20McpToolRegistryProvider) | Creates MCP tool registries that connect to MCP servers through various transport mechanisms (stdio, SSE). |

## Getting started

### 1. Set up an MCP connection

To use MCP with Koog, you need to set up a connection:

1. Start an MCP server (either as a process, Docker container, or web service).
2. Create a transport mechanism to communicate with the server. 

MCP servers support the stdio and SSE transport mechanisms to communicate with the agent, so you can connect using one of them.

#### Connect with stdio

This protocol is used when an MCP server runs as a separate process. Here is an example of setting up an MCP connection using the stdio transport:

```kotlin
// Start an MCP server (for example, as a process)
val process = ProcessBuilder("path/to/mcp/server").start()

// Create the stdio transport 
val transport = McpToolRegistryProvider.defaultStdioTransport(process)
```

#### Connect with SSE

This protocol is used when an MCP server runs as a web service. Here is an example of setting up an MCP connection using the SSE transport:

```kotlin
// Create the SSE transport
val transport = McpToolRegistryProvider.defaultSseTransport("http://localhost:8931")
```

### 2. Create a tool registry

Once you have the MCP connection, you can create a tool registry with tools from the MCP server in one of the following ways:

* Using the provided transport mechanism for communication. For example:

    ```kotlin
    // Create a tool registry with tools from the MCP server
    val toolRegistry = McpToolRegistryProvider.fromTransport(
        transport = transport,
        name = "my-client",
        version = "1.0.0"
    )
    ```

* Using an MCP client connected to the MCP server. For example:

    ```kotlin
    // Create a tool registry from an existing MCP client
    val toolRegistry = McpToolRegistryProvider.fromClient(
        mcpClient = existingMcpClient
    )
    ```

### 3. Integrate with your agent

To use MCP tools with your Koog agent, you need to register the tool registry with the agent:

```kotlin
// Create an agent with the tools
val agent = AIAgent(
    promptExecutor = executor,
    strategy = strategy,
    agentConfig = agentConfig,
    toolRegistry = toolRegistry
)

// Run the agent with a task that uses an MCP tool
val result = agent.run("Use the MCP tool to perform a task")
```

## Working directly with MCP tools

In addition to running tools through the agent, you can also run them directly:

1. Retrieve a specific tool from the tool registry.
2. Run the tool with specific arguments using the standard Koog mechanism.

Here is an example:

```kotlin
// Get a tool 
val tool = toolRegistry.getTool("tool-name") as McpTool

// Create arguments for the tool
val args = McpTool.Args(buildJsonObject { 
    put("parameter1", "value1")
    put("parameter2", "value2")
})

// Run the tool with the given arguments
val toolResult = tool.execute(args)

// Print the result
println(toolResult)
```

You can also retrieve all available MCP tools from the registry:

```kotlin
// Get all tools
val tools = toolRegistry.tools
```

## Usage examples

### Google Maps MCP integration

This example demonstrates how to connect to a [Google Maps](https://mcp.so/server/google-maps/modelcontextprotocol) server for geographic data using MCP:

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
val agent = AIAgent(
    executor = simpleOpenAIExecutor(openAIApiToken),
    llmModel = OpenAIModels.Chat.GPT4o,
    toolRegistry = toolRegistry,
)
agent.run("Get elevation of the Jetbrains Office in Munich, Germany?")
```

### Playwright MCP integration

This example demonstrates how to connect to a [Playwright](https://mcp.so/server/playwright-mcp/microsoft) server for web automation using MCP:

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
val agent = AIAgent(
    executor = simpleOpenAIExecutor(openAIApiToken),
    llmModel = OpenAIModels.Chat.GPT4o,
    toolRegistry = toolRegistry,
)
agent.run("Open a browser, navigate to jetbrains.com, accept all cookies, click AI in toolbar")
```