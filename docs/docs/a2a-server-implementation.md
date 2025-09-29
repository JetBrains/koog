# A2A server implementation

This page provides details about the A2A server implementation in the Koog agentic framework.

## Overview

The A2A server is responsible for handling requests from A2A clients according to the [A2A protocol specification](https://a2a-protocol.org/latest/specification/). It processes client requests, executes agent logic, manages task state and lifecycle, streams partial results, and handles push notifications.

The main class for the A2A server implementation is `A2AServer`, which implements the `RequestHandler` interface to process all A2A protocol operations.

## Key components

### A2AServer

The `A2AServer` class is the main entry point for server-side A2A protocol interactions. It performs the following functions:

- Processes client requests via the `RequestHandler` interface
- Delegates agent logic to the `AgentExecutor`
- Manages task state and lifecycle
- Handles streaming of partial results
- Configures and sends push notifications
- Provides error handling and reporting

### AgentExecutor

The `AgentExecutor` interface defines methods for executing agent logic. It is responsible for processing messages and generating responses.

### Storage components

The A2A server uses several storage components to manage state:

- **TaskStorage**: Stores and retrieves tasks
- **MessageStorage**: Stores and retrieves messages
- **PushNotificationConfigStorage**: Stores and retrieves push notification configurations

By default, the A2A server uses in-memory implementations of these storage components, but you can provide your own implementations for production use.

### Session management

The A2A server uses a session management system to handle the lifecycle of tasks:

- **Session**: Represents a single task execution session
- **SessionManager**: Manages the lifecycle of sessions
- **SessionEventProcessor**: Processes events generated during session execution

## Basic usage

### Creating an A2A server

To create an A2A server, you need to provide an `AgentExecutor` implementation and an `AgentCard`:

```kotlin
val agentExecutor = MyAgentExecutor()
val agentCard = AgentCard(
    name = "My Agent",
    description = "A helpful agent",
    version = "1.0.0",
    capabilities = AgentCapabilities(
        streaming = true,
        pushNotifications = false,
        stateTransitionHistory = false,
    )
)

val server = A2AServer(
    agentExecutor = agentExecutor,
    agentCard = agentCard
)
```
<!--- KNIT example-a2a-server-implementation-01.kt -->

### Implementing an AgentExecutor

The `AgentExecutor` is where your agent's logic lives. Here's a simple example:

```kotlin
class MyAgentExecutor : AgentExecutor {
    override suspend fun execute(
        message: Message,
        context: RequestContext
    ): Flow<Event> = flow {
        // Process the message
        val response = Message(
            role = Role.Agent,
            parts = listOf(TextPart(text = "Hello, I'm an agent!")),
            taskId = context.taskId,
            contextId = context.contextId
        )
        
        // Emit the response message
        emit(response)
        
        // Emit a task status update
        emit(
            TaskStatusUpdateEvent(
                taskId = context.taskId,
                contextId = context.contextId,
                status = TaskStatus(
                    state = TaskState.Completed
                ),
                final = true
            )
        )
    }
}
```
<!--- KNIT example-a2a-server-implementation-02.kt -->

### Handling requests

The A2A server implements the `RequestHandler` interface to handle client requests. Here's how it processes a message send request:

```kotlin
override suspend fun onSendMessage(
    request: Request<MessageSendParams>,
    ctx: ServerCallContext
): Response<CommunicationEvent> {
    // Validate the request
    val params = request.data
    val message = params.message
    
    // Create a session for the task
    val session = sessionManager.getOrCreateSession(
        message = message,
        ctx = ctx
    )
    
    // Execute the agent logic
    val events = agentExecutor.execute(
        message = message,
        context = session.context
    )
    
    // Process the events
    val result = session.processEvents(events).first()
    
    // Return the response
    return Response(data = result)
}
```
<!--- KNIT example-a2a-server-implementation-03.kt -->

### Streaming responses

If the agent supports streaming, it can provide partial results to the client:

```kotlin
override fun onSendMessageStreaming(
    request: Request<MessageSendParams>,
    ctx: ServerCallContext
): Flow<Response<Event>> {
    // Check if streaming is supported
    checkStreamingSupport()
    
    // Process the request and return a flow of responses
    return onSendMessageCommon(request, ctx)
}
```
<!--- KNIT example-a2a-server-implementation-04.kt -->

### Push notifications

If the agent supports push notifications, it can send updates to configured endpoints:

```kotlin
// Configure push notifications
override suspend fun onSetTaskPushNotificationConfig(
    request: Request<TaskPushNotificationConfig>,
    ctx: ServerCallContext
): Response<TaskPushNotificationConfig> {
    // Check if push notifications are supported
    val storage = storageIfPushNotificationSupported()
    
    // Store the configuration
    val config = request.data
    storage.storeConfig(config)
    
    // Return the configuration
    return Response(data = config)
}
```
<!--- KNIT example-a2a-server-implementation-05.kt -->

## Error Handling

The A2A server throws `A2AException` subclasses for various error conditions:

```kotlin
try {
    // Process the request
    val result = processRequest(request, ctx)
    return Response(data = result)
} catch (e: A2AInvalidParamsException) {
    // Handle invalid parameters
    throw e
} catch (e: A2AUnsupportedOperationException) {
    // Handle unsupported operations
    throw e
} catch (e: A2AException) {
    // Handle other A2A protocol errors
    throw e
} catch (e: Exception) {
    // Handle unexpected errors
    throw A2AInternalErrorException("Internal server error", e)
}
```
<!--- KNIT example-a2a-server-implementation-06.kt -->

## Advanced usage

### Custom storage

In addition to the generic in-memory storage implementation, you can add your own storage components for production use:

```kotlin
class MyTaskStorage : TaskStorage {
    override suspend fun storeTask(task: Task) {
        // Store the task in a database
    }
    
    override suspend fun getTask(taskId: String): Task? {
        // Retrieve the task from a database
    }
    
    // Implement other methods...
}
```
<!--- KNIT example-a2a-server-implementation-07.kt -->

### Authentication and authorization

For production deployments, extend the A2A server to add authentication and authorization:

```kotlin
class AuthorizedA2AServer(
    agentExecutor: AgentExecutor,
    agentCard: AgentCard,
    private val authService: AuthService,
) : A2AServer(
    agentExecutor = agentExecutor,
    agentCard = agentCard,
) {
    override suspend fun onSendMessage(
        request: Request<MessageSendParams>,
        ctx: ServerCallContext
    ): Response<CommunicationEvent> {
        // Authenticate and authorize the request
        val user = authenticateAndAuthorize(ctx, "send_message")
        
        // Pass user data to the agent executor via context state
        val enrichedCtx = ctx.copy(
            state = ctx.state + (AuthStateKeys.USER to user)
        )
        
        // Call the parent implementation with the enriched context
        return super.onSendMessage(request, enrichedCtx)
    }
    
    private suspend fun authenticateAndAuthorize(
        ctx: ServerCallContext,
        requiredPermission: String
    ): AuthenticatedUser {
        // Implement authentication and authorization logic
    }
}
```
<!--- KNIT example-a2a-server-implementation-08.kt -->

### Custom transport

You can implement your own `ServerTransport` to use different communication protocols or add custom behavior:

```kotlin
class MyServerTransport : ServerTransport {
    override val requestHandler: RequestHandler = myA2AServer
    
    // Implement transport-specific logic
}
```
<!--- KNIT example-a2a-server-implementation-09.kt -->
