# A2A Server

The A2A server enables you to expose AI agents through the standardized A2A protocol. It handles client requests, executes agent logic, manages task lifecycles, and supports streaming responses.

## Overview

The A2A server implements the [A2A protocol specification](https://a2a-protocol.org/latest/specification/) and provides:

- **Request Processing**: Handles all A2A protocol operations via `RequestHandler` interface
- **Agent Execution**: Delegates logic to your custom `AgentExecutor` implementation
- **Task Management**: Tracks task state and lifecycle with storage backends
- **Streaming Support**: Optional streaming of partial results to clients
- **Push Notifications**: Optional webhook notifications for task updates

## Core Components

### A2AServer

Main server class that implements the A2A protocol:

```kotlin
class A2AServer(
    agentExecutor: AgentExecutor,
    agentCard: AgentCard,
    // Optional storage implementations
    taskStorage: TaskStorage? = null,
    messageStorage: MessageStorage? = null,
    pushNotificationConfigStorage: PushNotificationConfigStorage? = null
) : RequestHandler
```

### AgentExecutor

Your custom agent logic implementation:

```kotlin
interface AgentExecutor {
    suspend fun execute(
        context: RequestContext<MessageSendParams>,
        eventProcessor: SessionEventProcessor
    )

    suspend fun cancel(
        context: RequestContext<TaskIdParams>,
        eventProcessor: SessionEventProcessor,
        agentJob: Deferred<Unit>?
    ) = Unit
}
```

### AgentCard

Describes your agent's capabilities and metadata:

```kotlin
val agentCard = AgentCard(
    name = "My Agent",
    protocolVersion = "0.3.0",
    description = "A helpful AI assistant",
    version = "1.0.0",
    preferredTransport = TransportProtocol.JSONRPC,
    capabilities = AgentCapabilities(
        streaming = false,
        pushNotifications = false
    )
)
```


### Storage Components

Optional storage backends (defaults to in-memory):

- **TaskStorage**: Persists task state and history
- **MessageStorage**: Stores conversation messages
- **PushNotificationConfigStorage**: Manages webhook configurations

## Quick Start

### 1. Create an AgentExecutor

```kotlin
class EchoAgentExecutor : AgentExecutor {
    override suspend fun execute(
        context: RequestContext<MessageSendParams>,
        eventProcessor: SessionEventProcessor
    ) {
        val userMessage = context.params.message
        val userText = userMessage.parts
            .filterIsInstance<TextPart>()
            .joinToString(" ") { it.text }

        // Echo the user's message back
        val response = Message(
            messageId = UUID.randomUUID().toString(),
            role = Role.Agent,
            parts = listOf(TextPart("You said: $userText")),
            contextId = context.contextId,
            taskId = context.taskId
        )

        eventProcessor.sendMessage(response)
    }
}
```

### 2. Create the Server

```kotlin
val agentCard = AgentCard(
    name = "Echo Agent",
    description = "Echoes back user messages",
    version = "1.0.0",
    capabilities = AgentCapabilities(
        streaming = false,
        pushNotifications = false
    )
)

val server = A2AServer(
    agentExecutor = EchoAgentExecutor(),
    agentCard = agentCard
)
```

### 3. Add Transport Layer

```kotlin
// HTTP JSON-RPC transport
val transport = HttpJSONRPCServerTransport(server)
transport.start(
    engineFactory = CIO,
    port = 8080,
    path = "/agent",
    wait = true
)
```

## Agent Implementation Patterns

### Simple Response Agent

```kotlin
class SimpleAgentExecutor : AgentExecutor {
    override suspend fun execute(
        context: RequestContext<MessageSendParams>,
        eventProcessor: SessionEventProcessor
    ) {
        val response = Message(
            messageId = UUID.randomUUID().toString(),
            role = Role.Agent,
            parts = listOf(TextPart("Hello from agent!")),
            contextId = context.contextId,
            taskId = context.taskId
        )

        eventProcessor.sendMessage(response)
    }
}
```

### Task-Based Agent

```kotlin
class TaskAgentExecutor : AgentExecutor {
    override suspend fun execute(
        context: RequestContext<MessageSendParams>,
        eventProcessor: SessionEventProcessor
    ) {
        // Send working status
        eventProcessor.sendTaskEvent(
            TaskStatusUpdateEvent(
                contextId = context.contextId,
                taskId = context.taskId,
                status = TaskStatus(
                    state = TaskState.Working,
                    timestamp = Clock.System.now()
                ),
                final = false
            )
        )

        // Do work...
        delay(1000)

        // Send completion
        eventProcessor.sendTaskEvent(
            TaskStatusUpdateEvent(
                contextId = context.contextId,
                taskId = context.taskId,
                status = TaskStatus(
                    state = TaskState.Completed,
                    timestamp = Clock.System.now()
                ),
                final = true
            )
        )
    }
}
```

### Streaming Agent

Enable streaming in your AgentCard:

```kotlin
val agentCard = AgentCard(
    capabilities = AgentCapabilities(
        streaming = true  // Enable streaming
    )
)
```

Then send multiple events:

```kotlin
class StreamingAgentExecutor : AgentExecutor {
    override suspend fun execute(
        context: RequestContext<MessageSendParams>,
        eventProcessor: SessionEventProcessor
    ) {
        repeat(3) { i ->
            val message = Message(
                messageId = UUID.randomUUID().toString(),
                role = Role.Agent,
                parts = listOf(TextPart("Chunk ${i + 1}")),
                contextId = context.contextId,
                taskId = context.taskId
            )
            eventProcessor.sendMessage(message)
            delay(500) // Simulate work
        }
    }
}
```

## Advanced Configuration

### Custom Storage

```kotlin
class DatabaseTaskStorage : TaskStorage {
    override suspend fun update(task: Task) {
        // Store in your database
    }

    override suspend fun get(taskId: String): Task? {
        // Retrieve from your database
    }
}

val server = A2AServer(
    agentExecutor = myExecutor,
    agentCard = agentCard,
    taskStorage = DatabaseTaskStorage()
)
```

### Authentication

Configure authentication in your AgentCard:

```kotlin
val agentCard = AgentCard(
    authentication = AgentAuthentication(
        type = "bearer",
        instructions = "Provide JWT token in Authorization header"
    )
)
```

### Push Notifications

```kotlin
val agentCard = AgentCard(
    capabilities = AgentCapabilities(
        pushNotifications = true
    )
)
```

## Error Handling

The server automatically handles A2A protocol errors. For custom error handling in your AgentExecutor:

```kotlin
override suspend fun execute(
    context: RequestContext<MessageSendParams>,
    eventProcessor: SessionEventProcessor
) {
    try {
        // Your agent logic
    } catch (e: Exception) {
        val errorMessage = Message(
            messageId = UUID.randomUUID().toString(),
            role = Role.Agent,
            parts = listOf(TextPart("Error: ${e.message}")),
            contextId = context.contextId,
            taskId = context.taskId
        )
        eventProcessor.sendMessage(errorMessage)
    }
}
```
