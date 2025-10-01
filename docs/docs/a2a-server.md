# A2A Server

The A2A server enables you to expose AI agents through the standardized A2A (Agent-to-Agent) protocol. It provides a complete implementation of the [A2A protocol specification](https://a2a-protocol.org/latest/specification/), handling client requests, executing agent logic, managing complex task lifecycles, and supporting real-time streaming responses.

## Overview

The A2A server acts as a bridge between the A2A protocol transport layer and your custom agent logic. 
It orchestrates the entire request lifecycle while maintaining protocol compliance and providing robust session management.

## Core Components

### A2AServer

The main server class implementing the complete A2A protocol. It serves as the central coordinator that:

- **Validates** incoming requests against protocol specifications
- **Manages** concurrent sessions and task lifecycles
- **Orchestrates** communication between transport, storage, and business logic layers
- **Handles** all protocol operations: message sending, task querying, cancellation, push notifications

```kotlin
class A2AServer(
    agentExecutor: AgentExecutor,           // Your business logic implementation
    agentCard: AgentCard,                   // Agent capabilities and metadata
    agentCardExtended: AgentCard? = null,   // Optional extended capabilities for authenticated users
    taskStorage: TaskStorage = InMemoryTaskStorage(),
    messageStorage: MessageStorage = InMemoryMessageStorage(),
    pushConfigStorage: PushNotificationConfigStorage? = null,
    pushSender: PushNotificationSender? = null,
    idGenerator: IdGenerator = UuidIdGenerator,
    coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob())
) : RequestHandler
```

### AgentExecutor

The `AgentExecutor` interface is where you implement your agent's core business logic. It acts as the bridge between the A2A protocol and your specific AI agent capabilities.

```kotlin
interface AgentExecutor {
    /**
     * Execute your agent's logic for an incoming message.
     * This is where you process user input, perform AI operations,
     * and send responses or task updates.
     */
    suspend fun execute(
        context: RequestContext<MessageSendParams>,  // Rich context with request data
        eventProcessor: SessionEventProcessor        // Send messages/task events
    )

    /**
     * Handle task cancellation requests.
     * Default implementation throws A2ATaskNotCancelableException.
     */
    suspend fun cancel(
        context: RequestContext<TaskIdParams>,
        eventProcessor: SessionEventProcessor,
        agentJob: Deferred<Unit>?                   // The running agent job to cancel
    ) = Unit
}
```

#### RequestContext

The `RequestContext` provides rich information about the current request:

```kotlin
data class RequestContext<T>(
    val callContext: ServerCallContext,      // Transport-level context (headers, auth, etc.)
    val params: T,                           // The actual request parameters
    val taskStorage: ContextTaskStorage,     // Scoped storage for this context
    val messageStorage: ContextMessageStorage, // Message history for this context
    val contextId: String,                   // Unique conversation identifier
    val taskId: String,                      // Current or new task identifier
    val task: Task?                          // Existing task if continuing one
)
```

#### SessionEventProcessor

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
        AgentInterface("https://rest.example.com/v1", TransportProtocol.HTTP_JSON_REST)
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

The A2A server supports multiple transport protocols for communicating with clients. 
The transport layer handles the low-level communication while the A2A server manages the protocol logic.

#### HTTP JSON-RPC Transport

The most common transport for A2A agent

```kotlin
val transport = HttpJSONRPCServerTransport(server)
transport.start(
    engineFactory = CIO,           // Ktor engine (CIO, Netty, Jetty)
    port = 8080,                   // Server port
    path = "/a2a",                 // API endpoint path
    host = "0.0.0.0",             // Bind address (optional)
    wait = true                    // Block until server stops
)
```

### Storage

The A2A server uses a pluggable storage architecture that separates different types of data.
All storage implementations are optional and default to in-memory variants for development.

- **TaskStorage**: Task lifecycle management - stores and manages task states, history, and artifacts
- **MessageStorage**: Conversation history - manages message history within conversation contexts
- **PushNotificationConfigStorage**: Webhook management - manages webhook configurations for asynchronous notifications

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
