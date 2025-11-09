# A2A Server

The A2A server enables you to expose AI agents through the standardized A2A (Agent-to-Agent) protocol. It provides a complete implementation of the [A2A protocol specification](https://a2a-protocol.org/latest/specification/), handling client requests, executing agent logic, managing complex task lifecycles, and supporting real-time streaming responses.

## Dependencies

To use the A2A server in your project, add the following dependencies to your `build.gradle.kts`:

```kotlin
dependencies {
    // Core A2A server library
    implementation("ai.koog:a2a-server:$koogVersion")

    // HTTP JSON-RPC transport (most common)
    implementation("ai.koog:a2a-transport-server-jsonrpc-http:$koogVersion")

    // Ktor server engine (choose one that fits your needs)
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
}
```

## Overview

The A2A server acts as a bridge between the A2A protocol transport layer and your custom agent logic. 
It orchestrates the entire request lifecycle while maintaining protocol compliance and providing robust session management.

## Core components

### A2AServer

The main server class implementing the complete A2A protocol. It serves as the central coordinator that:

- **Validates** incoming requests against protocol specifications
- **Manages** concurrent sessions and task lifecycles
- **Orchestrates** communication between transport, storage, and business logic layers
- **Handles** all protocol operations: message sending, task querying, cancellation, push notifications

The `A2AServer` accepts two required parameters:

* `AgentExecutor` which defines business logic implementation of the agent
* `AgentCard` which defines agent capabilities and metadata

And a number of optional parameters that can be used to customize its storage and transport behavior.

### AgentExecutor

The `AgentExecutor` interface is where you implement your agent's core business logic. 
It acts as the bridge between the A2A protocol and your specific AI agent capabilities.
To start the execution of your agent, you must implement the `execute` method where define your agent's logic.
To cancel the agent, you must implement the `cancel` method.

```kotlin
class MyAgentExecutor : AgentExecutor {
    override suspend fun execute(
        context: RequestContext<MessageSendParams>,
        eventProcessor: SessionEventProcessor
    ) {
        // Agent logic here
    }

    override suspend fun cancel(
        context: RequestContext<TaskIdParams>,
        eventProcessor: SessionEventProcessor,
        agentJob: Deferred<Unit>?
    ) {
        // Cancel agent here, optional
    }
}
```

The `RequestContext` provides rich information about the current request,
including the `contextId` and `taskId` of the current session, the `message` sent, and the `params` of the request.

The `SessionEventProcessor` communicates with clients:

- **`sendMessage(message)`**: Send immediate responses (chat-style interactions)
- **`sendTaskEvent(event)`**: Send task-related updates (long-running operations)

```kotlin
// For immediate responses (like chatbots)
eventProcessor.sendMessage(
    Message(
        messageId = generateId(),
        role = Role.Agent,
        parts = listOf(TextPart("Here's your answer!")),
        contextId = context.contextId
    )
)

// For task-based operations
eventProcessor.sendTaskEvent(
    TaskStatusUpdateEvent(
        contextId = context.contextId,
        taskId = context.taskId,
        status = TaskStatus(
            state = TaskState.Working,
            message = Message(/* progress update */),
            timestamp = Clock.System.now()
        ),
        final = false  // More updates to come
    )
)
```

### AgentCard

The `AgentCard` serves as your agent's self-describing manifest. It tells clients what your agent can do, how to communicate with it, and what security requirements it has.

```kotlin
val agentCard = AgentCard(
    // Basic Identity
    name = "Advanced Recipe Assistant",
    description = "AI agent specialized in cooking advice, recipe generation, and meal planning",
    version = "2.1.0",
    protocolVersion = "0.3.0",

    // Communication Settings
    url = "https://api.example.com/a2a",
    preferredTransport = TransportProtocol.JSONRPC,

    // Optional: Multiple transport support
    additionalInterfaces = listOf(
        AgentInterface("https://api.example.com/a2a", TransportProtocol.JSONRPC),
    ),

    // Capabilities Declaration
    capabilities = AgentCapabilities(
        streaming = true,              // Support real-time responses
        pushNotifications = true,      // Send async notifications
        stateTransitionHistory = true  // Maintain task history
    ),

    // Content Type Support
    defaultInputModes = listOf("text/plain", "text/markdown", "image/jpeg"),
    defaultOutputModes = listOf("text/plain", "text/markdown", "application/json"),

    // Define available security schemes
    securitySchemes = mapOf(
        "bearer" to HTTPAuthSecurityScheme(
            scheme = "Bearer",
            bearerFormat = "JWT",
            description = "JWT token authentication"
        ),
        "api-key" to APIKeySecurityScheme(
            `in` = In.Header,
            name = "X-API-Key",
            description = "API key for service authentication"
        )
    ),

    // Specify security requirements (logical OR of requirements)
    security = listOf(
        mapOf("bearer" to listOf("read", "write")),  // Option 1: JWT with read/write scopes
        mapOf("api-key" to emptyList())              // Option 2: API key
    ),

    // Enable extended card for authenticated users
    supportsAuthenticatedExtendedCard = true,
    
    // Skills/Capabilities
    skills = listOf(
        AgentSkill(
            id = "recipe-generation",
            name = "Recipe Generation",
            description = "Generate custom recipes based on ingredients, dietary restrictions, and preferences",
            tags = listOf("cooking", "recipes", "nutrition"),
            examples = listOf(
                "Create a vegan pasta recipe with mushrooms",
                "I have chicken, rice, and vegetables. What can I make?"
            )
        ),
        AgentSkill(
            id = "meal-planning",
            name = "Meal Planning",
            description = "Plan weekly meals and generate shopping lists",
            tags = listOf("meal-planning", "nutrition", "shopping")
        )
    ),

    // Optional: Branding
    iconUrl = "https://example.com/agent-icon.png",
    documentationUrl = "https://docs.example.com/recipe-agent",
    provider = AgentProvider(
        organization = "CookingAI Inc.",
        url = "https://cookingai.com"
    )
)
```

### Transport Layer

The A2A itself supports multiple transport protocols for communicating with clients. 
Currently, Koog provides implementations for JSON-RPC server transport over HTTP.

#### HTTP JSON-RPC Transport

```kotlin
val transport = HttpJSONRPCServerTransport(server)
transport.start(
    engineFactory = CIO,           // Ktor engine (CIO, Netty, Jetty)
    port = 8080,                   // Server port
    path = "/a2a",                 // API endpoint path
    wait = true                    // Block until server stops
)
```

### Storage

The A2A server uses a pluggable storage architecture that separates different types of data.
All storage implementations are optional and default to in-memory variants for development.

- **TaskStorage**: Task lifecycle management - stores and manages task states, history, and artifacts
- **MessageStorage**: Conversation history - manages message history within conversation contexts
- **PushNotificationConfigStorage**: Webhook management - manages webhook configurations for asynchronous notifications

## Quickstart

### 1. Create AgentCard
Define your agent's capabilities and metadata.
```kotlin
val agentCard = AgentCard(
    name = "IO Assistant",
    description = "AI agent specialized in input modification",
    version = "2.1.0",
    protocolVersion = "0.3.0",

    // Communication Settings
    url = "https://api.example.com/a2a",
    preferredTransport = TransportProtocol.JSONRPC,

    // Capabilities Declaration
    capabilities =
        AgentCapabilities(
            streaming = true,              // Support real-time responses
            pushNotifications = true,      // Send async notifications
            stateTransitionHistory = true  // Maintain task history
        ),

    // Content Type Support
    defaultInputModes = listOf("text/plain", "text/markdown", "image/jpeg"),
    defaultOutputModes = listOf("text/plain", "text/markdown", "application/json"),

    // Skills/Capabilities
    skills = listOf(
        AgentSkill(
            id = "echo",
            name = "echo",
            description = "Echoes back user messages",
            tags = listOf("io"),
        )
    )
)
```


### 2. Create an AgentExecutor
In executor manages implement agent logic, handles incoming requests and sends responses.

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
Pass the agent executor and agent card to the server.

```kotlin
val server = A2AServer(
    agentExecutor = EchoAgentExecutor(),
    agentCard = agentCard
)
```

### 3. Add Transport Layer
Create a transport layer and start the server.
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
If your agent only needs to respond to a single message, you can implement it as a simple agent. 
It can be also used if agent execution logic is not complex and time-consuming.

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
If the execution logic of your agent is complex and requires multiple steps, you can implement it as a task-based agent.
It can be also used if agent execution logic is time-consuming and suspending.
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
