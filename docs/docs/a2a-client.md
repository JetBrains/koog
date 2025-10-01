# A2A Client

The A2A client enables you to communicate with A2A-compliant agents over the network. 
It provides a complete implementation of the [A2A protocol specification](https://a2a-protocol.org/latest/specification/), handling agent discovery, message exchange, task management, and real-time streaming responses.

## Overview

The A2A client acts as a bridge between your application and A2A-compliant agents. 
It orchestrates the entire communication lifecycle while maintaining protocol compliance and providing robust session management.

## Core Components

### A2AClient

The main client class implementing the complete A2A protocol. It serves as the central coordinator that:

- **Manages** connections and agent discovery through pluggable resolvers
- **Orchestrates** message exchange and task operations with automatic protocol compliance
- **Handles** streaming responses and real-time communication when supported by agents
- **Provides** comprehensive error handling and fallback mechanisms for robust applications

```kotlin
class A2AClient(
    private val transport: ClientTransport,        // Network communication layer
    private val agentCardResolver: AgentCardResolver  // Agent discovery and metadata retrieval
) {
    /**
     * Connect to the agent and retrieve its capabilities.
     * This discovers what the agent can do and caches the AgentCard.
     */
    suspend fun connect(): AgentCard

    /**
     * Send a message to the agent and receive a single response.
     * Use this for simple request-response patterns.
     */
    suspend fun sendMessage(request: Request<MessageSendParams>): Response<CommunicationEvent>

    /**
     * Send a message with streaming support for real-time responses.
     * Returns a Flow of events including partial messages and task updates.
     */
    fun sendMessageStreaming(request: Request<MessageSendParams>): Flow<Response<Event>>

    /**
     * Query the status and details of a specific task.
     */
    suspend fun getTask(request: Request<TaskQueryParams>): Response<Task>

    /**
     * Cancel a running task if the agent supports cancellation.
     */
    suspend fun cancelTask(request: Request<TaskIdParams>): Response<Task>

    /**
     * Get the cached agent card without making a network request.
     * Returns null if connect() hasn't been called yet.
     */
    fun cachedAgentCard(): AgentCard?
}
```

### ClientTransport

The `ClientTransport` interface handles the low-level network communication while the A2A client manages the protocol logic. 
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

The `AgentCardResolver` interface retrieves agent metadata and capabilities. It enables agent discovery from various sources and supports caching strategies for optimal performance.

#### URL Agent Card Resolver

Fetch agent cards from HTTP endpoints following A2A conventions:

```kotlin
val agentCardResolver = UrlAgentCardResolver(
    baseUrl = "https://agent.example.com",           // Base URL of the agent service
    path = "/.well-known/agent-card.json",           // Standard agent card location
    httpClient = HttpClient(CIO),                    // Optional: custom HTTP client
    authenticatedPath = "/.well-known/agent-card-extended.json"  // Optional: extended card for authenticated users
)
```

## Quick Start

### 1. Create the Client

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

```kotlin
// Connect and retrieve agent capabilities
val agentCard = client.connect()

println("Connected to: ${agentCard.name}")
println("Supports streaming: ${agentCard.capabilities.streaming}")
```

### 3. Send Messages

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
    is Message -> println("Agent: ${event.parts.joinToString {
        (it as? TextPart)?.text ?: ""
    }}")
    is TaskEvent -> println("Task ${event.taskId} status: ${event.status}")
}
```

## Client Implementation Patterns

### Streaming Responses

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

### Task Management

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

### Simple Chat Client

```kotlin
class SimpleChatClient : AgentChatClient {
    override suspend fun execute(
        client: A2AClient,
        userInput: String
    ) {
        val message = Message(
            messageId = UUID.randomUUID().toString(),
            role = Role.User,
            parts = listOf(TextPart(userInput)),
            contextId = "chat-session-${UUID.randomUUID()}"
        )

        val request = Request(data = MessageSendParams(message))

        // Send message and receive response
        val response = client.sendMessage(request)
        when (val event = response.data) {
            is Message -> {
                val text = event.parts
                    .filterIsInstance<TextPart>()
                    .joinToString { it.text }
                println("Agent: $text")
            }
            is TaskEvent -> {
                println("Task ${event.taskId} started: ${event.status.state}")
            }
        }
    }
}
```

### Task-Based Client

```kotlin
class TaskBasedClient : AgentTaskClient {
    override suspend fun execute(
        client: A2AClient,
        taskDescription: String,
        contextId: String
    ) {
        val message = Message(
            messageId = UUID.randomUUID().toString(),
            role = Role.User,
            parts = listOf(TextPart(taskDescription)),
            contextId = contextId
        )

        val request = Request(data = MessageSendParams(message))

        // Send initial request and handle task creation
        when (val event = client.sendMessage(request).data) {
            is TaskEvent -> {
                println("Task ${event.taskId} created: ${event.status.state}")

                // Monitor task progress
                monitorTask(client, event.taskId)
            }
            is Message -> {
                val text = event.parts
                    .filterIsInstance<TextPart>()
                    .joinToString { it.text }
                println("Immediate response: $text")
            }
        }
    }

    private suspend fun monitorTask(client: A2AClient, taskId: String) {
        var taskCompleted = false

        while (!taskCompleted) {
            val taskRequest = Request(data = TaskQueryParams(taskId = taskId))
            val task = client.getTask(taskRequest).data

            println("Task status: ${task.status.state}")

            when (task.status.state) {
                TaskState.Completed, TaskState.Failed, TaskState.Cancelled -> {
                    taskCompleted = true
                    if (task.status.message != null) {
                        val text = task.status.message.parts
                            .filterIsInstance<TextPart>()
                            .joinToString { it.text }
                        println("Final result: $text")
                    }
                }
                else -> delay(1000) // Poll every second
            }
        }
    }
}
```
