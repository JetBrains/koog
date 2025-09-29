# A2A client implementation

This page provides details about the A2A client implementation in the Koog agentic framework.

## Overview

The A2A client is responsible for sending requests to A2A servers according to the [A2A protocol specification](https://a2a-protocol.org/latest/specification/). It handles connecting to agent services, retrieving agent capabilities, sending messages, managing tasks, and configuring push notifications.

The main class for the A2A client implementation is `A2AClient`, which provides methods for all A2A protocol operations.

## Key components

### A2AClient

The `A2AClient` class is the main entry point for client-side A2A protocol interactions. It:

- Manages connections to A2A servers
- Retrieves and caches Agent Cards
- Provides methods for all A2A protocol operations
- Validates capabilities before making requests

### AgentCardResolver

The `AgentCardResolver` is responsible for retrieving the Agent Card from the server. It provides information about the agent's capabilities and metadata based on the information in the card.

### ClientTransport

The `ClientTransport` interface defines methods for making requests to the A2A server. It handles the actual communication between the client and the server, including serialization, deserialization, and error handling.

## Basic usage

### Creating an A2A client

To create an A2A client, you need to provide a `ClientTransport` implementation and an `AgentCardResolver`:

```kotlin
val transport = HttpJSONRPCClientTransport(url = "https://example.com/a2a")
val agentCardResolver = UrlAgentCardResolver(baseUrl = "https://example.com/a2a/.well-known/agent-card.json")
val client = A2AClient(transport, agentCardResolver)
```
<!--- KNIT example-a2a-client-implementation-01.kt -->

### Connecting to an agent

Before using the client, you should connect to the agent to retrieve its capabilities:

```kotlin
// Connect to the agent and retrieve its Agent Card
client.connect()

// Get the cached Agent Card
val agentCard = client.cachedAgentCard()
```
<!--- KNIT example-a2a-client-implementation-02.kt -->

### Sending messages

To send a message to the agent:

```kotlin
val message = Message(
    role = Role.User,
    parts = listOf(TextPart(text = "Hello, agent!"))
)

val request = Request(
    data = MessageSendParams(message = message)
)

val response = client.sendMessage(request)
val event = response.data // CommunicationEvent
```
<!--- KNIT example-a2a-client-implementation-03.kt -->

### Streaming messages

If the agent supports streaming, you can use the streaming API to receive partial results:

```kotlin
val message = Message(
    role = Role.User,
    parts = listOf(TextPart(text = "Generate a long response"))
)

val request = Request(
    data = MessageSendParams(message = message)
)

client.sendMessageStreaming(request).collect { response ->
    val event = response.data // Event
    // Process the event
}
```
<!--- KNIT example-a2a-client-implementation-04.kt -->

### Managing tasks

To retrieve a task:

```kotlin
val request = Request(
    data = TaskQueryParams(taskId = "task-123")
)

val response = client.getTask(request)
val task = response.data // Task
```
<!--- KNIT example-a2a-client-implementation-05.kt -->

To cancel a task:

```kotlin
val request = Request(
    data = TaskIdParams(taskId = "task-123")
)

val response = client.cancelTask(request)
val task = response.data // Task with updated status
```
<!--- KNIT example-a2a-client-implementation-06.kt -->

### Push notifications

If the agent supports push notifications, you can configure them for a task:

```kotlin
val config = TaskPushNotificationConfig(
    taskId = "task-123",
    endpoint = "https://example.com/webhook",
    events = listOf("status-update", "artifact-update"),
    headers = mapOf("Authorization" to "Bearer token")
)

val request = Request(data = config)

val response = client.setTaskPushNotificationConfig(request)
```
<!--- KNIT example-a2a-client-implementation-07.kt -->

## Error handling

The A2A client throws `A2AException` subclasses for various error conditions:

```kotlin
try {
    val response = client.sendMessage(request)
    // Process the response
} catch (e: A2AInvalidParamsException) {
    // Handle invalid parameters
} catch (e: A2AUnsupportedOperationException) {
    // Handle unsupported operations
} catch (e: A2AException) {
    // Handle other A2A protocol errors
} catch (e: Exception) {
    // Handle transport or other errors
}
```
<!--- KNIT example-a2a-client-implementation-08.kt -->

## Advanced usage

### Custom Headers

You can provide custom headers for each request using `ClientCallContext`:

```kotlin
val ctx = ClientCallContext(
    additionalHeaders = mapOf(
        "Authorization" to listOf("Bearer token"),
        "X-Custom-Header" to listOf("value")
    )
)

val response = client.sendMessage(request, ctx)
```
<!--- KNIT example-a2a-client-implementation-09.kt -->

### Custom transport

You can implement your own `ClientTransport` to use different communication protocols or add custom behavior:

```kotlin
class MyCustomTransport : ClientTransport {
    override suspend fun sendMessage(
        request: Request<MessageSendParams>,
        ctx: ClientCallContext
    ): Response<CommunicationEvent> {
        // Custom implementation
    }
    
    // Implement other methods...
}
```
<!--- KNIT example-a2a-client-implementation-10.kt -->

### Custom Agent Card resolver

You can implement your own `AgentCardResolver` to retrieve the Agent Card in a custom way:

```kotlin
class MyCustomAgentCardResolver : AgentCardResolver {
    override suspend fun resolve(): AgentCard {
        // Custom implementation to retrieve the Agent Card
    }
}
```
<!--- KNIT example-a2a-client-implementation-11.kt -->
