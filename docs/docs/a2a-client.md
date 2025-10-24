# A2A Client

The A2A client enables you to communicate with A2A-compliant agents over the network.
It provides a complete implementation of
the [A2A protocol specification](https://a2a-protocol.org/latest/specification/), handling agent discovery, message
exchange, task management, and real-time streaming responses.

## Dependencies

To use the A2A client in your project, add the following dependencies to your `build.gradle.kts`:

```kotlin
dependencies {
    // Core A2A client library
    implementation("ai.koog:a2a-client:$koogVersion")

    // HTTP JSON-RPC transport (most common)
    implementation("ai.koog:a2a-transport-client-jsonrpc-http:$koogVersion")

    // Ktor client engine (choose one that fits your needs)
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
}
```

## Overview

The A2A client acts as a bridge between your application and A2A-compliant agents.
It orchestrates the entire communication lifecycle while maintaining protocol compliance and providing robust session
management.

## Core components

### A2AClient

The main client class implementing the complete A2A protocol. It serves as the central coordinator that:

- **Manages** connections and agent discovery through pluggable resolvers
- **Orchestrates** message exchange and task operations with automatic protocol compliance
- **Handles** streaming responses and real-time communication when supported by agents
- **Provides** comprehensive error handling and fallback mechanisms for robust applications

The `A2AClient` accepts two required parameters:

* `ClientTransport` which handles network communication layer
* `AgentCardResolver` which handles agent discovery and metadata retrieval

The `A2AClient` interface provides several key methods for interacting with A2A agents:

* `connect` method - To connect to the agent and retrieve its capabilities, which discovers what the agent can do and
  caches the AgentCard
* `sendMessage` method - To send a message to the agent and receive a single response for simple request-response
  patterns
* `sendMessageStreaming` method - To send a message with streaming support for real-time responses, which returns a Flow
  of events including partial messages and task updates
* `getTask` method - To query the status and details of a specific task
* `cancelTask` method - To cancel a running task if the agent supports cancellation
* `cachedAgentCard` method - To get the cached agent card without making a network request, which returns null if
  connect hasn't been called yet

### ClientTransport

The `ClientTransport` interface handles the low-level network communication while the A2A client manages the protocol
logic.
It abstracts away transport-specific details, allowing you to use different protocols seamlessly.

#### HTTP JSON-RPC Transport

The most common transport for A2A agents:

```kotlin
val transport = HttpJSONRPCClientTransport(
    url = "https://agent.example.com/a2a",        // Agent endpoint URL
    httpClient = HttpClient(CIO) {                // Optional: custom HTTP client
        install(ContentNegotiation) {
            json()
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30000
        }
    }
)
```

### AgentCardResolver

The `AgentCardResolver` interface retrieves agent metadata and capabilities.
It enables agent discovery from various sources and supports caching strategies for optimal performance.

#### URL Agent Card Resolver

Fetch agent cards from HTTP endpoints following A2A conventions:

```kotlin
val agentCardResolver = UrlAgentCardResolver(
    baseUrl = "https://agent.example.com",           // Base URL of the agent service
    path = "/.well-known/agent-card.json",           // Standard agent card location
    httpClient = HttpClient(CIO),                    // Optional: custom HTTP client
)
```

## Quickstart

### 1. Create the Client

Define the transport and agent card resolver and create the client.

```kotlin
// HTTP JSON-RPC transport
val transport = HttpJSONRPCClientTransport(
    url = "https://agent.example.com/a2a"
)

// Agent card resolver
val agentCardResolver = UrlAgentCardResolver(
    baseUrl = "https://agent.example.com",
    path = "/.well-known/agent-card.json"
)

// Create client
val client = A2AClient(transport, agentCardResolver)
```

### 2. Connect and Discover

Connect to the agent and retrieve its card.
Having agent's card enables you to query its capabilities and perform other operations, for example, check if it
supports streaming.

```kotlin
// Connect and retrieve agent capabilities
client.connect()
val agentCard = client.cachedAgentCard()

println("Connected to: ${agentCard.name}")
println("Supports streaming: ${agentCard.capabilities.streaming}")
```

### 3. Send Messages

Send a message to the agent and receive a single response.
The response can be either the message if the agent responded directly, or a task event if the agent is performing a
task.

```kotlin
val message = Message(
    messageId = UUID.randomUUID().toString(),
    role = Role.User,
    parts = listOf(TextPart("Hello, agent!")),
    contextId = "conversation-1"
)

val request = Request(data = MessageSendParams(message))
val response = client.sendMessage(request)

// Handle response
when (val event = response.data) {
    is Message -> {
        val text = event.parts
            .filterIsInstance<TextPart>()
            .joinToString { it.text }
        print(text) // Stream partial responses
    }
    is TaskEvent -> {
        if (event.final) {
            println("\nTask completed")
        }
    }
}
```

### 4. Send Messages Streaming

The A2A client supports streaming responses for real-time communication.
Instead of receiving a single response, it returns a `Flow` of events including messages and task updates.

```kotlin
// Check if agent supports streaming
if (client.cachedAgentCard()?.capabilities?.streaming == true) {
    client.sendMessageStreaming(request).collect { response ->
        when (val event = response.data) {
            is Message -> {
                val text = event.parts
                    .filterIsInstance<TextPart>()
                    .joinToString { it.text }
                print(text) // Stream partial responses
            }
            is TaskStatusUpdateEvent -> {
                if (event.final) {
                    println("\nTask completed")
                }
            }
        }
    }
} else {
    // Fallback to non-streaming
    val response = client.sendMessage(request)
    // Handle single response
}
```

### 5. Manage Tasks

A2A Client provides methods to control server tasks by asking for their status and cancelling them.

```kotlin
// Query task status
val taskRequest = Request(data = TaskQueryParams(taskId = "task-123"))
val taskResponse = client.getTask(taskRequest)
val task = taskResponse.data

println("Task state: ${task.status.state}")

// Cancel running task
if (task.status.state == TaskState.Working) {
    val cancelRequest = Request(data = TaskIdParams(taskId = "task-123"))
    val cancelledTask = client.cancelTask(cancelRequest).data
    println("Task cancelled: ${cancelledTask.status.state}")
}
```
