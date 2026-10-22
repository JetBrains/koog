---
status: beta
---

# A2A Server

--8<-- "versioning-snippets.md:beta"

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
It orchestrates the entire request lifecycle while maintaining protocol compliance and providing robust turn management.

## Core components

### A2AServer

The main server class implementing the complete A2A protocol. It serves as the central coordinator that:

- **Validates** incoming requests against protocol specifications
- **Manages** concurrent turns and task lifecycles
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
        context: RequestContext<SendMessageRequest>,
        eventProcessor: TurnEventProcessor
    ) {
        // Agent logic here
    }

    override suspend fun cancel(
        context: RequestContext<CancelTaskRequest>,
        eventProcessor: TurnEventProcessor,
        agentJob: Deferred<Unit>?
    ) {
        // Cancel agent here, optional
    }
}
```

The `RequestContext` provides rich information about the current request,
including the `contextId` and `taskId` of the current turn, the `params` of the request
(for example, `params.message` for a sent message), the stored `task` (if the request refers to an existing task),
and the `callContext` with the request headers and custom state.

The `TurnEventProcessor` communicates with clients:

- **`sendMessage(message)`**: Send immediate responses (chat-style interactions)
- **`sendTaskEvent(event)`**: Send task-related updates (long-running operations)

```kotlin
// For immediate responses (like chatbots)
eventProcessor.sendMessage(
    Message(
        messageId = generateId(),
        role = Role.ROLE_AGENT,
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
            state = TaskState.TASK_STATE_WORKING,
            message = Message(/* progress update */),
            timestamp = Clock.System.now()
        )
    )
)
```

There is no `final` flag on status updates: a turn ends when `execute` returns,
and a terminal task state ends the task.

#### Error handling in AgentExecutor

How the server treats an exception thrown by `execute` depends on whether the agent has already sent an event in this turn:

- **An `A2AException` thrown before the first event** rejects the request: the client gets that error
  (for example, `A2AContentTypeNotSupportedException` or `A2AUnsupportedOperationException`),
  and the task, if it exists, stays unchanged. Use this to reject invalid input.
- **Any other exception, or any exception after the first event,** fails the turn: the server moves a non-terminal task
  to `TASK_STATE_FAILED` with a generic message and notifies all streams of the task.

```kotlin
override suspend fun execute(
    context: RequestContext<SendMessageRequest>,
    eventProcessor: TurnEventProcessor
) {
    if (context.params.message.parts.none { it is TextPart }) {
        // Nothing was sent yet, so the task keeps its state and the client gets the error code
        throw A2AContentTypeNotSupportedException("Only text input is supported")
    }

    // ...
}
```

An exception that an agent throws on its own, including a `CancellationException` (for example, from `withTimeout`),
is a failure too. Only a cancellation coming from the server (for example, CancelTask or shutdown) does not fail the task.

To customize the message of a failed task, override `failureMessage` in `A2AServer`:

```kotlin
class MyA2AServer(
    agentExecutor: AgentExecutor,
    agentCard: AgentCard
) : A2AServer(agentExecutor, agentCard) {
    override suspend fun failureMessage(taskId: String, contextId: String, error: Throwable): Message =
        Message(
            messageId = idGenerator.generateMessageId(taskId, contextId),
            role = Role.ROLE_AGENT,
            parts = listOf(
                TextPart(
                    if (error is TimeoutCancellationException) "The agent took too long to answer."
                    else "The agent failed to answer."
                )
            )
        )
}
```

`failureMessage` is called when the server marks a task as failed because its turn failed.
The server sets the task id, context id, and role of the returned message itself. Clients see this message,
so don't put exception details in it unless they are meant for the client. If the override throws, the default message
("Task failed due to an internal error") is used. Keep it short: until it returns, the turn has not ended,
so follow-up requests to the same task wait.

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

To mount the transport into an existing Ktor application instead, use the `a2aJsonRpcTransportRoute` and
`a2aAgentCardRoute` routes (the `SSE` plugin must be installed):

```kotlin
embeddedServer(Netty, port = 8080) {
    install(SSE)

    routing {
        a2aJsonRpcTransportRoute("/a2a", transport)
        a2aAgentCardRoute("/.well-known/agent-card.json", agentCard)
    }
}.startSuspend(wait = true)
```

#### Customizing the call context

Every request is handled with a `ServerCallContext` that holds the request headers and a typed `state` map.
Agents read it from `RequestContext.callContext`. By default, the state is empty. To fill it from the HTTP call, for example
with the authenticated user, pass `customizeCallContext` to `a2aJsonRpcTransportRoute` or to `HttpJSONRPCServerTransport.start`.
The function is suspending and runs for every parsed JSON-RPC request, with the Ktor `ApplicationCall` as the receiver.

```kotlin
val UserKey = StateKey<String>("user")

embeddedServer(Netty, port = 8080) {
    install(SSE)
    install(Authentication) { // requires io.ktor:ktor-server-auth
        basic("auth-basic") { /* validate credentials */ }
    }

    routing {
        authenticate("auth-basic") {
            a2aJsonRpcTransportRoute("/a2a", transport) { ctx ->
                ctx.copy(state = ctx.state + (UserKey to principal<UserIdPrincipal>()!!.name))
            }
        }
    }
}.startSuspend(wait = true)
```

With the standalone transport, pass it to `start`. Here the user comes from a token that is checked by your own code.
The function only enriches the context: to reject unauthenticated requests, use Ktor authentication on the route
or check the state in your executor.

```kotlin
transport.start(
    engineFactory = CIO,
    port = 8080,
    path = "/a2a",
    customizeCallContext = { ctx ->
        val token = request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")
        val user = token?.let { verifyToken(it) } // returns the user name or null
        if (user != null) ctx.copy(state = ctx.state + (UserKey to user)) else ctx
    }
)
```

The agent then reads the value:

```kotlin
override suspend fun execute(
    context: RequestContext<SendMessageRequest>,
    eventProcessor: TurnEventProcessor
) {
    val user = context.callContext.getFromStateOrNull(UserKey)
        ?: throw A2AInvalidRequestException("Authentication required")
    // ...
}
```

The customization runs before the `A2A-Version` header is checked. An `A2AException` thrown from it is answered as
a JSON-RPC error with the exception's code, and any other exception as an internal error.
The key type and the stored value must match, otherwise `getFromState` and `getFromStateOrNull` throw.

#### Detecting disconnected streaming clients

A server can only tell that a streaming client has gone away when a write to it fails, so a stream that stays idle
(for example, a subscription to a task waiting for input) would otherwise be held until the task ends or the server
stops. Both `a2aJsonRpcTransportRoute` and `HttpJSONRPCServerTransport.start` therefore send an SSE comment line
(`: heartbeat`) every `heartbeatPeriod`, 15 seconds by default, so that the stream is released after the connection
is closed. Pass a shorter period for faster cleanup, or `null` to disable heartbeats:

```kotlin
transport.start(
    engineFactory = CIO,
    port = 8080,
    path = "/a2a",
    heartbeatPeriod = 5.seconds
)
```

Detection is best-effort and depends on the engine: it needs a failing write and takes a few periods. With Ktor 3.3.3
on Netty, a client that closes with unread data (a TCP reset) may not be detected, and its stream then ends with the
task or when the server stops.

### Storage

The A2A server uses a pluggable storage architecture that separates different types of data.
All storage implementations are optional and default to in-memory variants for development.

- **TaskStorage**: Task lifecycle management - stores and manages task states, history, and artifacts
- **MessageStorage**: Conversation history - manages message history within conversation contexts
- **PushNotificationConfigStorage**: Webhook management - manages webhook configurations for asynchronous notifications

## Shutting down

`A2AServer` has two ways to stop:

- **`shutdown(gracePeriod)`** is the graceful one. It is a `suspend` function that waits for the work in flight for
  at most `gracePeriod` and returns once the server is shut down. The default period is zero, which shuts down at once.
- **`cancel()`** shuts down immediately without waiting, as `shutdown()` with no grace period does.

During the grace period, the server accepts no new work: `SendMessage`, `SendStreamingMessage`, and new
`SubscribeToTask` requests fail with the JSON-RPC error `-32603` "Server is shutting down", including a message that
waits for the running turn of its task. Other operations, such as `GetTask`, `ListTasks`, `CancelTask`, and push
notification config operations, are still accepted. After the shutdown, every request fails with `-32603`
"Server is shut down".

During the grace period, the server waits for:

1. every running turn (`AgentExecutor.execute` call) to end, so its client gets the usual response;
2. then every pending push notification to be sent.

Open `SubscribeToTask` streams are not waited for. They stay open during the grace period and keep receiving
the events of the running turns. Then, when both waits are over or the grace period has passed, whichever comes first,
the server shuts down as `cancel()` does:

- Open streams end. A stream that has already delivered the terminal state of its task ends normally; any other
  stream gets `-32603` "Server is shut down" as its last event, and a blocking `SendMessage` that is still waiting
  fails with it.
- The agents that are still running are cancelled. Tasks keep their last persisted state: they are not moved to
  `TASK_STATE_FAILED` or `TASK_STATE_CANCELED`.
- Push notifications that are not sent yet are dropped.

`shutdown` can be called concurrently and repeatedly: all calls wait for the same shutdown, and the earliest deadline
wins. Calling `cancel()` or cancelling the calling coroutine shuts the server down at once, and the waiting calls return.
Cancelling the `coroutineScope` that you passed to `A2AServer` from outside also counts as a shutdown, while
`shutdown` and `cancel` never cancel that scope.

Call `a2aServer.shutdown(...)` **before** stopping the transport or the Ktor server. If the HTTP server stops first,
the connections of the clients are cut while the agents keep running, and the clients see a broken connection instead of
the final events.

```kotlin
val server = embeddedServer(Netty, port = 8080) {
    install(SSE)

    routing {
        a2aJsonRpcTransportRoute("/a2a", transport)
        a2aAgentCardRoute("/.well-known/agent-card.json", agentCard)
    }
}.startSuspend(wait = false)

// Later, when the application stops:
a2aServer.shutdown(gracePeriod = 30.seconds) // drain the turns first
server.stopSuspend(gracePeriod = 1_000, timeoutMillis = 5_000) // then stop the HTTP server
```

With the standalone `HttpJSONRPCServerTransport`, call `a2aServer.shutdown(...)` and then `transport.stop()`.

The shutdown is bounded except for a few things that it cannot interrupt:

- an agent that ignores cancellation may keep running after the shutdown;
- storage writes that the server runs as non-cancellable (persisting an event, saving an inline push notification
  config), so a slow storage delays the shutdown;
- a running `AgentExecutor.cancel` of a `CancelTask` request: the turns of that task can't end until it returns.

To add your own checks for every operation, override `A2AServer.checkAccepting(method, ctx)` and call `super`
to keep the shutdown check and the required-extensions check.

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
        context: RequestContext<SendMessageRequest>,
        eventProcessor: TurnEventProcessor
    ) {
        val userMessage = context.params.message
        val userText = userMessage.parts
            .filterIsInstance<TextPart>()
            .joinToString(" ") { it.text }

        // Echo the user's message back
        val response = Message(
            messageId = UUID.randomUUID().toString(),
            role = Role.ROLE_AGENT,
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
        context: RequestContext<SendMessageRequest>,
        eventProcessor: TurnEventProcessor
    ) {
        val response = Message(
            messageId = UUID.randomUUID().toString(),
            role = Role.ROLE_AGENT,
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
The first event for a new task must be the `Task` itself; after that, send status and artifact updates.
```kotlin
class TaskAgentExecutor : AgentExecutor {
    override suspend fun execute(
        context: RequestContext<SendMessageRequest>,
        eventProcessor: TurnEventProcessor
    ) {
        // Create the task in the working state
        eventProcessor.sendTaskEvent(
            Task(
                id = context.taskId,
                contextId = context.contextId,
                status = TaskStatus(
                    state = TaskState.TASK_STATE_WORKING,
                    timestamp = Clock.System.now()
                )
            )
        )

        // Do work...

        // Send completion
        eventProcessor.sendTaskEvent(
            TaskStatusUpdateEvent(
                contextId = context.contextId,
                taskId = context.taskId,
                status = TaskStatus(
                    state = TaskState.TASK_STATE_COMPLETED,
                    timestamp = Clock.System.now()
                )
            )
        )
    }
}
```
