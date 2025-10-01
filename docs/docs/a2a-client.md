# A2A Client

The A2A client enables you to communicate with A2A-compliant agents over the network. It handles connection management, agent discovery, message exchange, and task operations.

## Overview

The A2A client implements the [A2A protocol specification](https://a2a-protocol.org/latest/specification/) and provides:

- **Agent Discovery**: Retrieves and caches AgentCard metadata
- **Message Exchange**: Send messages and receive responses
- **Task Management**: Query, cancel, and monitor tasks
- **Streaming Support**: Receive partial results in real-time
- **Push Notifications**: Configure webhook callbacks for updates

## Core Components

### A2AClient

Main client class for A2A protocol operations:

```kotlin
class A2AClient(
    private val transport: ClientTransport,
    private val agentCardResolver: AgentCardResolver
) {
    suspend fun connect(): AgentCard
    suspend fun sendMessage(request: Request<MessageSendParams>): Response<CommunicationEvent>
    fun sendMessageStreaming(request: Request<MessageSendParams>): Flow<Response<Event>>
    suspend fun getTask(request: Request<TaskQueryParams>): Response<Task>
    suspend fun cancelTask(request: Request<TaskIdParams>): Response<Task>
}
```

### ClientTransport

Handles the actual network communication:

- **HttpJSONRPCClientTransport**: HTTP JSON-RPC transport
- **Custom transports**: Implement `ClientTransport` interface

### AgentCardResolver

Retrieves agent metadata:

- **UrlAgentCardResolver**: Fetch from HTTP endpoint
- **Custom resolvers**: Implement `AgentCardResolver` interface

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

## Usage Patterns

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

### Push Notifications

```kotlin
// Configure webhooks for task updates
if (client.cachedAgentCard()?.capabilities?.pushNotifications == true) {
    val config = TaskPushNotificationConfig(
        taskId = "task-123",
        endpoint = "https://myapp.com/webhooks/task-updates",
        events = listOf("status-update", "message"),
        headers = mapOf("Authorization" to "Bearer my-webhook-token")
    )

    val request = Request(data = config)
    client.setTaskPushNotificationConfig(request)
}
```

### Error Handling

```kotlin
try {
    val response = client.sendMessage(request)
    // Process response
} catch (e: A2AUnsupportedOperationException) {
    println("Agent doesn't support this operation")
} catch (e: A2AInvalidParamsException) {
    println("Invalid request parameters: ${e.message}")
} catch (e: A2AException) {
    println("A2A protocol error: ${e.message}")
} catch (e: Exception) {
    println("Network error: ${e.message}")
}
```

## Advanced Configuration

### Authentication

```kotlin
// Add authentication headers
val authContext = ClientCallContext(
    additionalHeaders = mapOf(
        "Authorization" to listOf("Bearer your-jwt-token")
    )
)

val response = client.sendMessage(request, authContext)
```

### Custom Transport

```kotlin
class WebSocketClientTransport(private val url: String) : ClientTransport {
    override suspend fun sendMessage(
        request: Request<MessageSendParams>,
        ctx: ClientCallContext
    ): Response<CommunicationEvent> {
        // WebSocket implementation
    }

    // Implement other required methods
}

val client = A2AClient(
    transport = WebSocketClientTransport("wss://agent.example.com"),
    agentCardResolver = myResolver
)
```

### Custom Agent Card Resolver

```kotlin
class DatabaseAgentCardResolver(private val agentId: String) : AgentCardResolver {
    override suspend fun resolve(): AgentCard {
        // Load from database, cache, service registry, etc.
        return myDatabase.getAgentCard(agentId)
    }
}

val client = A2AClient(
    transport = transport,
    agentCardResolver = DatabaseAgentCardResolver("agent-123")
)
```

## Complete Example

```kotlin
suspend fun chatWithAgent() {
    val transport = HttpJSONRPCClientTransport("https://agent.example.com/a2a")
    val resolver = UrlAgentCardResolver(
        baseUrl = "https://agent.example.com",
        path = "/.well-known/agent-card.json"
    )

    val client = A2AClient(transport, resolver)

    // Connect and get capabilities
    val agentCard = client.connect()
    println("Connected to: ${agentCard.name}")

    // Send message
    val message = Message(
        messageId = UUID.randomUUID().toString(),
        role = Role.User,
        parts = listOf(TextPart("What can you do?")),
        contextId = "demo-conversation"
    )

    val request = Request(data = MessageSendParams(message))

    try {
        if (agentCard.capabilities.streaming) {
            // Handle streaming response
            client.sendMessageStreaming(request).collect { response ->
                when (val event = response.data) {
                    is Message -> {
                        val text = event.parts
                            .filterIsInstance<TextPart>()
                            .joinToString { it.text }
                        print(text)
                    }
                }
            }
        } else {
            // Handle single response
            val response = client.sendMessage(request)
            when (val event = response.data) {
                is Message -> {
                    val text = event.parts
                        .filterIsInstance<TextPart>()
                        .joinToString { it.text }
                    println("Agent: $text")
                }
            }
        }
    } catch (e: A2AException) {
        println("Error: ${e.message}")
    }
}
```
