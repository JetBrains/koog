# A2A and Koog Integration

Koog provides seamless integration with the A2A protocol, allowing you to expose Koog agents as A2A servers and connect
Koog agents to other A2A-compliant agents.

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
                updatePrompt {
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
                updatePrompt {
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
agent.install(A2AAgentServer) {
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
    sendTaskUpdate("Processing your request...", TaskState.Working)
}
```

### Start A2A Server

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
transport.start(engineFactory = CIO, port = 8080, path = "/chat", wait = true)
```
