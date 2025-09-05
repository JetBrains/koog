# Module agents-features-debugger

The `agents-features-debugger` module provides comprehensive debugging capabilities for AI agents in the Koog framework. It allows developers to monitor and record events during an agent's operation, providing valuable insights into the execution flow and behavior of AI agents.

### Overview

The Debugger feature integrates into an AI agent's pipeline and intercepts various events such as agent start/finish, strategy execution, node execution, LLM calls, and tool operations. These events are collected and can be sent to a remote debugging server for real-time monitoring and analysis.

Key capabilities of the Debugger feature include:
- Monitoring the complete lifecycle of AI agent execution
- Tracking strategy and node executions
- Recording LLM calls and responses
- Logging tool operations and their results
- Capturing errors and exceptions during agent execution
- Connecting to a remote debugging server for real-time monitoring

### Using in your project

To use the Debugger feature in your project, you need to install it when creating an AI agent. The feature can be configured with custom settings or used with default values.

#### Basic Installation

```kotlin
// When creating an agent
val agent = createAgent(
    // ... other agent configuration
) {
    // Install the Debugger feature with default settings
    install(Debugger)
}
```

#### Custom Configuration

You can customize the Debugger by specifying a port for the debugging server:

```kotlin
val agent = createAgent(
    // ... other agent configuration
) {
    install(Debugger) {
        // Set a specific port for the debugging server
        setPort(8080)
    }
}
```

#### Port Configuration Priority

The Debugger feature determines the port to use in the following order:
1. Explicitly set port in the configuration (using `setPort()`)
2. Environment variable `KOOG_DEBUGGER_PORT`
3. Default Koog remote server port (50881)

### Using in unit tests

When writing unit tests for components that use the Debugger feature, you can configure it to use a specific port to avoid conflicts with other services. This is particularly useful in CI/CD environments where multiple tests might run simultaneously.

#### Test Configuration

```kotlin
// Find an available port for the test
val port = findAvailablePort()

// Create an agent with the Debugger feature configured for testing
val agent = createAgent(
    // ... other agent configuration
) {
    install(Debugger) {
        setPort(port)
    }
}

// Use the agent in your test
agent.use { 
    // Your test code here
}
```

#### Testing with a Client

You can also test the Debugger feature by creating a client that connects to the debugging server and collects events:

```kotlin
// Server configuration (agent with Debugger)
val port = findAvailablePort()
val agent = createAgent(
    // ... agent configuration
) {
    install(Debugger) {
        setPort(port)
    }
}

// Client configuration
val clientConfig = DefaultClientConnectionConfig(
    host = "127.0.0.1", 
    port = port
)

// Create a client to collect events
FeatureMessageRemoteClient(connectionConfig = clientConfig).use { client ->
    // Collect and verify events
    // ...
    
    // Run the agent
    agent.run(userPrompt)
}
```

### Example of usage

Here's a complete example of using the Debugger feature in a real-world scenario:

```kotlin
// Create a strategy for the agent
val strategy = strategy("example-strategy") {
    val nodeLLMRequest by nodeLLMRequest("llm-request-node")
    val nodeToolCall by nodeExecuteTool("tool-call-node")
    val nodeSendToolResult by nodeLLMSendToolResult("send-tool-result-node")

    edge(nodeStart forwardTo nodeLLMRequest)
    edge(nodeLLMRequest forwardTo nodeToolCall onToolCall { true })
    edge(nodeLLMRequest forwardTo nodeFinish onAssistantMessage { true })
    edge(nodeToolCall forwardTo nodeSendToolResult)
    edge(nodeSendToolResult forwardTo nodeFinish onAssistantMessage { true })
    edge(nodeSendToolResult forwardTo nodeToolCall onToolCall { true })
}

// Create a tool registry
val toolRegistry = ToolRegistry {
    tool(SearchTool())
    tool(CalculatorTool())
}

// Create an agent with the Debugger feature
val agent = createAgent(
    agentId = "example-agent",
    strategy = strategy,
    promptId = "example-prompt",
    systemPrompt = "You are a helpful assistant.",
    toolRegistry = toolRegistry,
    model = myLLModel
) {
    // Install and configure the Debugger feature
    install(Debugger) {
        // Use a specific port or let it use the default
        // setPort(8080)
    }
}

// Use the agent
agent.use { 
    // Run the agent with a user prompt
    val result = agent.run("Calculate 25 * 16 and then search for information about the result.")
    
    // Process the result
    println("Agent result: $result")
}
```

While the agent is running, the Debugger will collect events such as:
- Agent start and finish events
- Strategy execution events
- Node execution events
- LLM calls and responses
- Tool calls and their results

These events can be monitored through a debugging client connected to the specified port.
