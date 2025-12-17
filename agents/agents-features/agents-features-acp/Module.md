# Module agents-features-acp

A module provides integration with [Agent Client Protocol (ACP)](https://agentclientprotocol.com/).
The main components of the ACP integration in Koog are:

- [**AcpAgent**](src/jvmMain/kotlin/ai/koog/agents/features/acp/AcpAgent.kt): A feature that enables communication
  between Koog agents and ACP-compliant client applications
- [**MessageConverters**](src/jvmMain/kotlin/ai/koog/agents/features/acp/MessageConverters.kt): Utilities for converting
  messages between Koog and ACP formats

## Overview

### What is ACP?

The Agent Client Protocol (ACP) is a standardized protocol that enables AI agents to communicate with client
applications through a consistent interface.
ACP provides a bidirectional communication channel where agents can:

- Receive prompts from clients
- Send events and updates back to clients in real-time
- Report tool call status and progress
- Share reasoning and thoughts with clients
- Manage session lifecycle and state

To read more about ACP visit [https://agentclientprotocol.com](https://agentclientprotocol.com)

### How to Use ACP with Koog?

#### Setting Up an ACP-Enabled Agent

Koog integrates with ACP using the [ACP Kotlin SDK](https://github.com/agentclientprotocol/kotlin-sdk)
with the additional API extensions presented in the `agents-features-acp` module.

To use ACP with Koog, you need to:

1. Implement the `AgentSupport` and `AgentSession` interface from the ACP SDK
2. In `AgentSession.prompt` method initialize Koog agent with `AcpAgent` feature installed
3. Configure the feature with session ID, protocol instance, and events producer
4. Handle incoming prompts and convert them to Koog messages

Here's a basic example of setting up an ACP-enabled agent:

```kotlin
class KoogAgentSession(
    override val sessionId: SessionId,
    private val promptExecutor: PromptExecutor,
    private val protocol: Protocol,
    private val clock: Clock,
) : AgentSession {

    private var agentJob: Deferred<Unit>? = null
    private val agentMutex = Mutex()

    override suspend fun prompt(
        content: List<ContentBlock>,
        _meta: JsonElement?
    ): Flow<Event> = channelFlow {
        val agentConfig = AIAgentConfig(
            prompt = prompt("acp") {
                system("You are a helpful assistant.")
            }.appendPrompt(content),
            model = OpenAIModels.Chat.GPT4o,
            maxAgentIterations = 1000
        )

        agentMutex.withLock {
            val agent = AIAgent(
                promptExecutor = promptExecutor,
                agentConfig = agentConfig,
                strategy = yourStrategy(),
                toolRegistry = toolRegistry,
            ) {
                install(AcpAgent) {
                    this.sessionId = this@KoogAgentSession.sessionId.value
                    this.protocol = this@KoogAgentSession.protocol
                    this.eventsProducer = this@channelFlow
                    this.setDefaultNotifications = true
                }
            }

            agentJob = async { agent.run(Unit) }
            agentJob?.await()
        }
    }

    private fun Prompt.appendPrompt(content: List<ContentBlock>): Prompt {
        return withMessages { messages ->
            messages + listOf(content.toKoogMessage(clock))
        }
    }
}
```

Important notes:

* Use `channelFlow` to allow sending events from different corutines
* Set `this.setDefaultNotifications = true` to automatically handle standard ACP notifications using agent pipeline
  interseption. In case manual notification handling, please set `this.setDefaultNotifications = false` and process all
  the agents events accoring to the spesificaion using vai protocol `AcpAgent` feature.
* To convert ACP content blocks to Koog messages use `toKoogMessage` extension function and append recieved user message
  to the prompt.
* Run the agent in a separate coroutine to allow canceling in `AgentSession.cancel` method
* Use mutex` to synchronize access to the agent instance, as by current protocol prompt should not trigger new execution
  until previous is finished

#### Configuration Options

The `AcpAgent` feature can be configured through `AcpConfig`:

- `sessionId`: The unique session identifier for the ACP connection
- `protocol`: The protocol instance used for sending requests and notifications to ACP clients
- `eventsProducer`: A coroutine-based producer scope for sending events
- `setDefaultNotifications`: Whether to register default notification handlers (default: `true`)

#### Default Notification Handlers

When `setDefaultNotifications` is enabled, the AcpAgent feature automatically handles:

1. **Agent Completion**: Sends `PromptResponseEvent` with `StopReason.END_TURN` when the agent completes successfully
2. **Agent Execution Failures**: Sends `PromptResponseEvent` with appropriate stop reasons:
    - `StopReason.MAX_TURN_REQUESTS` for max iterations exceeded
    - `StopReason.REFUSAL` for other execution failures
3. **LLM Responses**: Converts and sends LLM responses as ACP events (text, tool calls, reasoning)
4. **Tool Call Lifecycle**: Reports tool call status changes:
    - `ToolCallStatus.IN_PROGRESS` when a tool call starts
    - `ToolCallStatus.COMPLETED` when a tool call succeeds
    - `ToolCallStatus.FAILED` when a tool call fails

#### Sending Custom Events

You can send custom events to the ACP client using the `sendEvent` method:

```kotlin
val agent = AIAgent(...) {
    install(AcpAgent) { ... }
}

// Later in your code, access the ACP feature
withAcpAgent {
    sendEvent(
        Event.SessionUpdateEvent(
            SessionUpdate.PlanUpdate(planEntries)
        )
    )
}
```

#### Message Conversion

The module provides utilities for converting between Koog and ACP message formats:

**ACP to Koog:**

```kotlin
// Convert ACP content blocks to Koog message
val koogMessage = acpContentBlocks.toKoogMessage(clock)

// Convert single ACP content block to Koog content part
val contentPart = acpContentBlock.toKoogContentPart()
```

**Koog to ACP:**

```kotlin
// Convert Koog response message to ACP events
val acpEvents = koogResponseMessage.toAcpEvents()

// Convert Koog content part to ACP content block
val acpContentBlock = koogContentPart.toAcpContentBlock()
```

### Supported Content Types

The ACP integration supports the following content types:

- **Text**: Plain text content
- **Image**: Image data with MIME type
- **Audio**: Audio data with MIME type
- **File**: File attachments (embedded or linked)
    - Base64-encoded binary data
    - Plain text data
    - URL references
- **Resource**: Embedded resources with URI and content
- **Resource Link**: Links to external resources

### Platform Support

The ACP feature is currently available only on the JVM platform, as it depends on the ACP Kotlin SDK which is
JVM-specific.

### Examples

Complete working examples can be found in `examples/simple-examples/src/main/kotlin/ai/koog/agents/example/acp/`.

How to run the example:
1. Run the AcpApp.kt file
```shell
./gradlew :examples:simple-examples:run 
```
2. Enter the request for ACP Agent
```shell
Move file `my-file.md` to folder `my-folder` and appent title `## My File` to the file content
```
3. Check the events traces in the console
