# A2A and Koog Integration

Koog provides seamless integration with the A2A protocol, allowing you to expose Koog agents as A2A servers and connect
Koog agents to other A2A-compliant agents.

## Dependencies

A2A Koog integration requires specific feature modules depending on your use case:

### For Exposing Koog Agents as A2A Servers

Add these dependencies to your `build.gradle.kts`:

```kotlin
dependencies {
    // Koog A2A server integration feature
    implementation("ai.koog:agents-features-a2a-server:$koogVersion")

    // HTTP JSON-RPC transport
    implementation("ai.koog:a2a-transport-server-jsonrpc-http:$koogVersion")

    // Ktor server engine (choose one that fits your needs)
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
}
```

### For Connecting Koog Agents to A2A Agents

Add these dependencies to your `build.gradle.kts`:

```kotlin
dependencies {
    // Koog A2A client integration feature
    implementation("ai.koog:agents-features-a2a-client:$koogVersion")

    // HTTP JSON-RPC transport
    implementation("ai.koog:a2a-transport-client-jsonrpc-http:$koogVersion")

    // Ktor client engine (choose one that fits your needs)
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
}
```

## Overview

The integration enables two main patterns:

1. **Expose Koog agents as A2A servers** - Make your Koog agents discoverable and accessible via the A2A protocol
2. **Connect Koog agents to A2A agents** - Let your Koog agents communicate with other A2A-compliant agents

## Exposing Koog Agents as A2A Servers

### Define Koog Agent with A2A feature

Let's define a Koog agent first. The logic of the agent can vary, but here's an example basic single run agent with
tools.
The agent resaves a message from the user, forwards it to the llm.
If the llm response contains a tool call, the agent executes the tool and forwards the result to the llm.
If the llm response contains an assistant message, the agent sends the assistant message to the user and finishes.

On input resize, the agent sends a task submitted event to the A2A client with the input message.
On each tool call, the agent sends a task working event to the A2A client with the tool call and result.
On assistant message, the agent sends a task complete event to the A2A client with the assistant message.

```kotlin
/**
 * Create a Koog agent with A2A feature
 */
@OptIn(ExperimentalUuidApi::class)
private fun createAgent(
    context: RequestContext<MessageSendParams>,
    eventProcessor: SessionEventProcessor,
) = AIAgent(
    promptExecutor = MultiLLMPromptExecutor(
        LLMProvider.Google to GoogleLLMClient("api-key")
    ),
    toolRegistry = ToolRegistry {
        // Declare tools here
    },
    strategy = strategy<A2AMessage, Unit>("test") {
        val nodeSetup by node<A2AMessage, Unit> { inputMessage ->
            // Convenience function to transform A2A message into Koog message
            val input = inputMessage.toKoogMessage()
            llm.writeSession {
                appendPrompt {
                    message(input)
                }
            }
            // Send update event to A2A client
            withA2AAgentServer {
                sendTaskUpdate("Request submitted: ${input.content}", TaskState.Submitted)
            }
        }

        // Calling llm
        val nodeLLMRequest by node<Unit, Message> {
            llm.writeSession {
                requestLLM()
            }
        }

        // Executing tool
        val nodeProcessTool by node<Message.Tool.Call, Unit> { toolCall ->
            withA2AAgentServer {
                sendTaskUpdate("Executing tool: ${toolCall.content}", TaskState.Working)
            }

            val toolResult = environment.executeTool(toolCall)

            llm.writeSession {
                appendPrompt {
                    tool {
                        result(toolResult)
                    }
                }
            }
            withA2AAgentServer {
                sendTaskUpdate("Tool result: ${toolResult.content}", TaskState.Working)
            }
        }

        // Sending assistant message
        val nodeProcessAssistant by node<String, Unit> { assistantMessage ->
            withA2AAgentServer {
                sendTaskUpdate(assistantMessage, TaskState.Completed)
            }
        }

        edge(nodeStart forwardTo nodeSetup)
        edge(nodeSetup forwardTo nodeLLMRequest)

        // If a tool call is returned from llm, forward to the tool processing node and then back to llm
        edge(nodeLLMRequest forwardTo nodeProcessTool onToolCall { true })
        edge(nodeProcessTool forwardTo nodeLLMRequest)

        // If an assistant message is returned from llm, forward to the assistant processing node and then to finish
        edge(nodeLLMRequest forwardTo nodeProcessAssistant onAssistantMessage { true })
        edge(nodeProcessAssistant forwardTo nodeFinish)
    },
    agentConfig = AIAgentConfig(
        prompt = prompt("agent") { system("You are a helpful assistant.") },
        model = GoogleModels.Gemini2_5Pro,
        maxAgentIterations = 10
    ),
) {
    install(A2AAgentServer) {
        this.context = context
        this.eventProcessor = eventProcessor
    }
}

/**
 * Convenience function to send task update event to A2A client
 * @param content The message content
 * @param state The task state
 */
@OptIn(ExperimentalUuidApi::class)
private suspend fun A2AAgentServer.sendTaskUpdate(
    content: String,
    state: TaskState,
) {
    val message = A2AMessage(
        messageId = Uuid.random().toString(),
        role = Role.Agent,
        parts = listOf(
            TextPart(content)
        ),
        contextId = context.contextId,
        taskId = context.taskId,
    )

    val task = Task(
        id = context.taskId,
        contextId = context.contextId,
        status = TaskStatus(
            state = state,
            message = message,
            timestamp = Clock.System.now(),
        )
    )
    eventProcessor.sendTaskEvent(task)
}
```

## A2AAgentServer Feature Mechanism

The `A2AAgentServer` is a Koog agent feature that enables seamless integration between Koog agents and the A2A protocol.
The `A2AAgentServer` feature provides access to the `RequestContext` and `SessionEventProcessor` entities, which are used to
communicate with the A2A client inside the Koog agent.

To install the feature, call the `install` function on the agent and pass the `A2AAgentServer` feature along with the `RequestContext` and `SessionEventProcessor`:
```kotlin
// Install the feature
install(A2AAgentServer) {
    this.context = context
    this.eventProcessor = eventProcessor
}
```

To access these entities from Koog agent strategy, the feature provides a `withA2AAgentServer` function that allows agent nodes to access A2A server capabilities within their execution context. 
It retrieves the installed `A2AAgentServer` feature and provides it as the receiver for the action block.

```kotlin
// Usage within agent nodes
withA2AAgentServer {
    // 'this' is now A2AAgentServer instance
    eventProcessor.sendTaskUpdate("Processing your request...", TaskState.Working)
}
```

### Start A2A Server
After running the server Koog agent will be discoverable and accessible via the A2A protocol.

```kotlin
val agentCard = AgentCard(
    name = "Koog Agent",
    url = "http://localhost:9999/koog",
    description = "Simple universal agent powered by Koog",
    version = "1.0.0",
    protocolVersion = "0.3.0",
    preferredTransport = TransportProtocol.JSONRPC,
    capabilities = AgentCapabilities(streaming = true),
    defaultInputModes = listOf("text"),
    defaultOutputModes = listOf("text"),
    skills = listOf(
        AgentSkill(
            id = "koog",
            name = "Koog Agent",
            description = "Universal agent powered by Koog. Supports tool calling.",
            tags = listOf("chat", "tool"),
        )
    )
)
// Server setup
val server = A2AServer(agentExecutor = KoogAgentExecutor(), agentCard = agentCard)
val transport = HttpJSONRPCServerTransport(server)
transport.start(engineFactory = Netty, port = 8080, path = "/chat", wait = true)
```

## Connecting Koog Agents to A2A Agents

### Create A2A Client and connect to the A2A Server

```kotlin
val transport = HttpJSONRPCClientTransport(url = "http://localhost:9999/koog")
val agentCardResolver =
    UrlAgentCardResolver(baseUrl = "http://localhost:9999", path = "/koog")
val client = A2AClient(transport = transport, agentCardResolver = agentCardResolver)

val agentId = "koog"
client.connect()
```

### Create Koog Agent and add A2A Client to A2AAgentClient Feature
To connect to A2A agent from your Koog Agent, you can use the A2AAgentClient feature, which provides a client API for connecting to A2A agents.
The principle of the client is the same as the server: you install the feature and pass the `A2AAgentClient` feature along with the `RequestContext` and `SessionEventProcessor`.

```kotlin
val agent = AIAgent(
    promptExecutor = MultiLLMPromptExecutor(
        LLMProvider.Google to GoogleLLMClient("api-key")
    ),
    toolRegistry = ToolRegistry {
        // declare tools here
    },
    strategy = strategy<String, Unit>("test") {

        val nodeCheckStreaming by nodeA2AClientGetAgentCard().transform { it.capabilities.streaming }

        val nodeA2ASendMessageStreaming by nodeA2AClientSendMessageStreaming()
        val nodeA2ASendMessage by nodeA2AClientSendMessage()

        val nodeProcessStreaming by node<Flow<Response<Event>>, Unit> {
            it.collect { response ->
                when (response.data) {
                    is Task -> {
                        // Process task
                    }

                    is A2AMessage -> {
                        // Process message
                    }

                    is TaskStatusUpdateEvent -> {
                        // Process task status update
                    }

                    is TaskArtifactUpdateEvent -> {
                        // Process task artifact update
                    }
                }
            }
        }

        val nodeProcessEvent by node<CommunicationEvent, Unit> { event ->
            when (event) {
                is Task -> {
                    // Process task
                }

                is A2AMessage -> {
                    // Process message
                }
            }
        }

        // If streaming is supported, send a message, process response and finish
        edge(nodeStart forwardTo nodeCheckStreaming transformed { agentId })
        edge(
            nodeCheckStreaming forwardTo nodeA2ASendMessageStreaming
                onCondition { it == true } transformed { buildA2ARequest(agentId) }
        )
        edge(nodeA2ASendMessageStreaming forwardTo nodeProcessStreaming)
        edge(nodeProcessStreaming forwardTo nodeFinish)

        // If streaming is not supported, send a message, process response and finish
        edge(
            nodeCheckStreaming forwardTo nodeA2ASendMessage
                onCondition { it == false } transformed { buildA2ARequest(agentId) }
        )
        edge(nodeA2ASendMessage forwardTo nodeProcessEvent)
        edge(nodeProcessEvent forwardTo nodeFinish)

        // If streaming is not supported, send a message, process response and finish
        edge(nodeCheckStreaming forwardTo nodeFinish onCondition { it == null }
            transformed { println("Failed to get agents card") }
        )

    },
    agentConfig = AIAgentConfig(
        prompt = prompt("agent") { system("You are a helpful assistant.") },
        model = GoogleModels.Gemini2_5Pro,
        maxAgentIterations = 10
    ),
) {
    install(A2AAgentClient) {
        this.a2aClients = mapOf(agentId to client)
    }
}


@OptIn(ExperimentalUuidApi::class)
private fun AIAgentGraphContextBase.buildA2ARequest(agentId: String): A2AClientRequest<MessageSendParams> =
    A2AClientRequest(
        agentId = agentId,
        callContext = ClientCallContext.Default,
        params = MessageSendParams(
            message = A2AMessage(
                messageId = Uuid.random().toString(),
                role = Role.User,
                parts = listOf(
                    TextPart(agentInput as String)
                )
            )
        )
    )
```
