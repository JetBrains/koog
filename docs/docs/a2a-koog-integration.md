# A2A and Koog Integration

Koog provides seamless integration with the A2A protocol, allowing you to expose Koog agents as A2A servers and connect Koog agents to other A2A-compliant agents.

## Overview

The integration enables two main patterns:

1. **Expose Koog agents as A2A servers** - Make your Koog agents discoverable and accessible via the A2A protocol
2. **Connect Koog agents to A2A agents** - Let your Koog agents communicate with other A2A-compliant agents

## Exposing Koog Agents as A2A Servers

### Using AgentExecutor Pattern

The primary way to wrap Koog functionality into an A2A server is by implementing the `AgentExecutor` interface:

```kotlin
class KoogA2AExecutor : AgentExecutor {
    private val promptExecutor = MultiLLMPromptExecutor(
        LLMProvider.OpenAI to OpenAILLMClient(ApiKeyService.openAIApiKey),
        LLMProvider.Anthropic to AnthropicLLMClient(ApiKeyService.anthropicApiKey)
    )

    override suspend fun execute(
        context: RequestContext<MessageSendParams>,
        eventProcessor: SessionEventProcessor
    ) {
        val userMessage = context.params.message

        // Save incoming A2A message
        context.messageStorage.save(userMessage)

        // Convert A2A messages to Koog messages
        val koogMessages = context.messageStorage.getAll()
            .map { it.toKoogMessage() }

        // Use Koog's prompt system
        val prompt = prompt("assistant") {
            system {
                +"You are a helpful AI assistant"
            }
            messages(koogMessages)
        }

        // Execute with Koog's LLM client
        val response = promptExecutor.execute(prompt, AnthropicModels.Sonnet_4)
            .single()
            .let { message ->
                (message as Message.Assistant).toA2AMessage(
                    a2aMetadata = MessageA2AMetadata(
                        messageId = UUID.randomUUID().toString(),
                        contextId = context.contextId
                    )
                )
            }

        // Save and send response
        context.messageStorage.save(response)
        eventProcessor.sendMessage(response)
    }
}
```

### Complete Server Setup

```kotlin
suspend fun startKoogA2AServer() {
    // Create agent card describing capabilities
    val agentCard = AgentCard(
        name = "Koog Assistant",
        description = "AI assistant powered by Koog framework",
        version = "1.0.0",
        protocolVersion = "0.3.0",
        preferredTransport = TransportProtocol.JSONRPC,
        capabilities = AgentCapabilities(
            streaming = false,
            pushNotifications = false
        ),
        skills = listOf(
            AgentSkill(
                id = "general_assistance",
                name = "General Assistance",
                description = "Provides helpful responses to user queries",
                examples = listOf("How do I...?", "What is...?", "Help me with...")
            )
        )
    )

    // Create A2A server with Koog-powered executor
    val server = A2AServer(
        agentExecutor = KoogA2AExecutor(),
        agentCard = agentCard
    )

    // Start HTTP JSON-RPC transport
    val transport = HttpJSONRPCServerTransport(server)
    transport.start(
        engineFactory = CIO,
        port = 8080,
        path = "/agent",
        wait = true
    )

    println("Koog A2A server started at http://localhost:8080/agent")
}
```

### Using Koog AIAgent Features

For more advanced integration, use Koog's AIAgent features:

```kotlin
// Install A2A server feature in AIAgent
val agent = aiAgent {
    install(A2AAgentServer) {
        // A2A server configuration
    }

    graph {
        // Use A2A-specific nodes
        val messages = nodeA2AMessageStorageLoad()
        val koogMessages = node { a2aMessages ->
            a2aMessages.map { it.toKoogMessage() }
        }
        val response = nodeLLMPrompt(AnthropicModels.Sonnet_4) {
            system { +"You are a helpful assistant" }
            messages(koogMessages)
        }
        val a2aResponse = node { message ->
            message.toA2AMessage(MessageA2AMetadata(
                messageId = UUID.randomUUID().toString(),
                contextId = context.contextId
            ))
        }
        nodeA2ARespondMessage(saveToStorage = true)(a2aResponse)
    }
}
```

## Connecting Koog Agents to A2A Agents

### Using A2AClient Directly

```kotlin
class KoogAgentWithA2AClient {
    private val a2aClient = A2AClient(
        transport = HttpJSONRPCClientTransport("https://other-agent.com/a2a"),
        agentCardResolver = UrlAgentCardResolver(
            baseUrl = "https://other-agent.com",
            path = "/.well-known/agent-card.json"
        )
    )

    suspend fun consultOtherAgent(userQuery: String): String {
        // Connect to remote agent
        val agentCard = a2aClient.connect()

        // Send message
        val message = Message(
            messageId = UUID.randomUUID().toString(),
            role = Role.User,
            parts = listOf(TextPart(userQuery)),
            contextId = "consultation-${UUID.randomUUID()}"
        )

        val request = Request(data = MessageSendParams(message))
        val response = a2aClient.sendMessage(request)

        // Extract response text
        return when (val event = response.data) {
            is Message -> event.parts
                .filterIsInstance<TextPart>()
                .joinToString { it.text }
            else -> "No text response received"
        }
    }
}
```

### Using AIAgent A2A Client Feature

```kotlin
val agent = aiAgent {
    install(A2AAgentClient) {
        client("expert-agent") {
            transport = HttpJSONRPCClientTransport("https://expert.com/a2a")
            agentCardResolver = UrlAgentCardResolver(
                baseUrl = "https://expert.com",
                path = "/.well-known/agent-card.json"
            )
        }
    }

    graph {
        val userMessage = input<Message.User>()

        // Convert to A2A message
        val a2aMessage = node { message ->
            message.toA2AMessage()
        }

        // Send to remote A2A agent
        val a2aRequest = node { message ->
            A2AClientRequest(
                agentId = "expert-agent",
                params = MessageSendParams(message)
            )
        }

        val a2aResponse = nodeA2AClientSendMessage()(a2aRequest)

        // Convert response back to Koog format
        val koogResponse = node { event ->
            when (event) {
                is Message -> event.toKoogMessage()
                else -> Message.Assistant("No response received")
            }
        }

        output(koogResponse)
    }
}
```

## Message Conversion

Koog provides automatic conversion between A2A and Koog message formats:

### A2A to Koog

```kotlin
// Convert A2A message to Koog message
val koogMessage: Message = a2aMessage.toKoogMessage()

// Handles role conversion
when (a2aMessage.role) {
    Role.User -> Message.User(content, metaInfo, attachments)
    Role.Agent -> Message.Assistant(content, metaInfo, attachments)
}
```

### Koog to A2A

```kotlin
// Convert Koog message to A2A message
val a2aMessage: A2AMessage = koogMessage.toA2AMessage(
    a2aMetadata = MessageA2AMetadata(
        messageId = UUID.randomUUID().toString(),
        contextId = "context-id"
    )
)
```

## Advanced Integration Patterns

### Multi-Agent Collaboration

```kotlin
val collaborativeAgent = aiAgent {
    install(A2AAgentClient) {
        client("researcher") { /* researcher agent config */ }
        client("writer") { /* writer agent config */ }
        client("reviewer") { /* reviewer agent config */ }
    }

    graph {
        val userQuery = input<Message.User>()

        // Step 1: Research
        val researchRequest = node { query ->
            A2AClientRequest(
                agentId = "researcher",
                params = MessageSendParams(query.toA2AMessage())
            )
        }
        val researchResults = nodeA2AClientSendMessage()(researchRequest)

        // Step 2: Write draft
        val writeRequest = node { research ->
            val prompt = "Based on this research: $research, write a comprehensive answer"
            A2AClientRequest(
                agentId = "writer",
                params = MessageSendParams(Message(
                    messageId = UUID.randomUUID().toString(),
                    role = Role.User,
                    parts = listOf(TextPart(prompt)),
                    contextId = "writing-task"
                ))
            )
        }
        val draft = nodeA2AClientSendMessage()(writeRequest)

        // Step 3: Review and finalize
        val reviewRequest = node { draft ->
            A2AClientRequest(
                agentId = "reviewer",
                params = MessageSendParams(/* review request */)
            )
        }
        val finalResponse = nodeA2AClientSendMessage()(reviewRequest)

        output(finalResponse)
    }
}
```

### Hybrid Local-Remote Processing

```kotlin
val hybridAgent = aiAgent {
    install(A2AAgentClient) {
        client("specialist") { /* specialist agent */ }
    }

    graph {
        val userMessage = input<Message.User>()

        // Decide whether to handle locally or remotely
        val shouldUseSpecialist = node { message ->
            message.content.contains("complex") ||
            message.content.contains("specialized")
        }

        // Branch: local processing
        val localResponse = nodeLLMPrompt(AnthropicModels.Sonnet_4) {
            system { +"You are a general assistant" }
            user { +userMessage.content }
        }

        // Branch: remote specialist
        val remoteRequest = node { message ->
            A2AClientRequest(
                agentId = "specialist",
                params = MessageSendParams(message.toA2AMessage())
            )
        }
        val remoteResponse = nodeA2AClientSendMessage()(remoteRequest)

        // Select response based on condition
        val finalResponse = node { (useSpecialist, local, remote) ->
            if (useSpecialist) {
                (remote as Message).toKoogMessage()
            } else {
                local
            }
        }

        output(finalResponse)
    }
}
```

## Best Practices

### 1. Error Handling

```kotlin
override suspend fun execute(
    context: RequestContext<MessageSendParams>,
    eventProcessor: SessionEventProcessor
) {
    try {
        // Your Koog logic here
    } catch (e: Exception) {
        // Send error message through A2A
        val errorMessage = Message(
            messageId = UUID.randomUUID().toString(),
            role = Role.Agent,
            parts = listOf(TextPart("Error processing request: ${e.message}")),
            contextId = context.contextId,
            taskId = context.taskId
        )
        eventProcessor.sendMessage(errorMessage)
    }
}
```

### 2. Resource Management

```kotlin
class ResourceManagedKoogExecutor : AgentExecutor, Closeable {
    private val promptExecutor = MultiLLMPromptExecutor(/*...*/)

    override suspend fun execute(/*...*/) {
        // Use resources
    }

    override fun close() {
        promptExecutor.close()
    }
}
```

### 3. Configuration

```kotlin
// Use environment-specific configuration
val agentCard = AgentCard(
    name = System.getenv("AGENT_NAME") ?: "Koog Agent",
    url = System.getenv("AGENT_URL") ?: "http://localhost:8080/agent",
    // ... other configuration
)
```

The A2A-Koog integration provides a powerful foundation for building interoperable AI agent systems that can leverage both Koog's rich agent framework and the broader A2A ecosystem.
