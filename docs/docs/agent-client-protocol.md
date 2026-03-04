# Agent Client Protocol

Agent Client Protocol (ACP) is an open-source, standardized protocol
that enables client applications to communicate with AI agents through a consistent, bidirectional interface.
By implementing ACP in your Koog agent,
you ensure it can easily integrate into any ACP-compliant environment, such as an IDE.

For more information, see the [Agent Client Protocol] documentation.

## Integration with Koog

The Koog framework integrates with ACP using the [ACP Kotlin SDK]
with additional API extensions.
This integration provides:

* Standardized communication for a Koog agent with ACP-compliant client applications
* Automatic execution updates for tool calls, agent thoughts, and completions
* Seamless message conversion between Koog's multimodal message formats and ACP's content blocks
* Lifecycle mapping of Koog agent states to ACP session events

!!! note

    Since [ACP Kotlin SDK] is JVM-specific,
    the ACP integration is currently available only on the JVM platform.

### Add dependencies

ACP support is an optional [feature](features-overview.md) that is not available in Koog by default.
To implement ACP for your Koog agent,
add a dependency for [ai.koog:agents-features-acp](https://mvnrepository.com/artifact/ai.koog/agents-features-acp),
which itself has a dependency on [com.agentclientprotocol:acp](https://mvnrepository.com/artifact/com.agentclientprotocol/acp).

For example, in case of `build.gradle.kts`:

```kotlin
dependencies {
    implementation("ai.koog:agents-features-acp:$koogVersion")
}
```

### Enable ACP for a Koog agent

To bridge a Koog agent's internal [event system](agent-events.md) with the ACP protocol,
install the `ai.koog.agents.features.acp.AcpAgent` feature.
When installed, it listens for lifecycle events (like tool calls or LLM responses)
and sends them to the ACP client.

<!--- CLEAR -->
<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.features.acp.AcpAgent
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor

-->
```kotlin
val agent = AIAgent(
    promptExecutor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY")),
    llmModel = OpenAIModels.Chat.GPT4o
) {
    install(AcpAgent) {
        this.sessionId = sessionId
        this.protocol = protocol
        this.eventsProducer = eventsProducer
        this.setDefaultNotifications = true
    }
}
```
<!--- KNIT example-agent-client-protocol-01.kt -->

Key configuration options:

*   **`sessionId`**: A unique string identifying the current conversation session.
*   **`protocol`**: An instance of [`com.agentclientprotocol.protocol.Protocol`](https://github.com/agentclientprotocol/kotlin-sdk/blob/master/acp/src/commonMain/kotlin/com/agentclientprotocol/protocol/Protocol.kt) used for low-level communication.
*   **`eventsProducer`**: A `kotlinx.coroutines.channels.ProducerScope<Event>` where ACP events are sent.
    For more information, see [Event streaming](#event-streaming).
*   **`setDefaultNotifications`**: Whether to register default notification handlers for agent lifecycle events.
    For more information, see [Handling agent notifications](#handling-agent-notifications).

This agent must run within the scope of an ACP session as described in the next chapter.

### Implement an ACP-enabled agent

To connect your Koog agent to ACP clients,
implement two core interfaces from the [ACP Kotlin SDK](https://github.com/agentclientprotocol/kotlin-sdk):

- [`AgentSupport`](https://github.com/agentclientprotocol/kotlin-sdk/blob/master/acp/src/commonMain/kotlin/com/agentclientprotocol/agent/AgentSupport.kt):
  Manages the agent's identity, capabilities, and session lifecycle (creating or loading sessions).
- [`AgentSession`](https://github.com/agentclientprotocol/kotlin-sdk/blob/master/acp/src/commonMain/kotlin/com/agentclientprotocol/agent/AgentSession.kt):
  Manages a single conversation session, handles the `prompt` execution, and manages cancellation.

Inside the `prompt()` method of `AgentSession` is where you should initialize and run the ACP-enabled Koog agent.
Here is an example:

=== "AgentSession"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.agents.core.agent.config.AIAgentConfig
    import ai.koog.agents.features.acp.AcpAgent
    import ai.koog.agents.features.acp.toKoogMessage
    import ai.koog.prompt.dsl.Prompt
    import ai.koog.prompt.dsl.prompt
    import ai.koog.prompt.executor.clients.openai.OpenAIModels
    import ai.koog.prompt.executor.model.PromptExecutor
    import com.agentclientprotocol.agent.AgentSession
    import com.agentclientprotocol.common.Event
    import com.agentclientprotocol.model.ContentBlock
    import com.agentclientprotocol.model.SessionId
    import com.agentclientprotocol.protocol.Protocol
    import kotlinx.coroutines.Deferred
    import kotlinx.coroutines.async
    import kotlinx.coroutines.flow.Flow
    import kotlinx.coroutines.flow.channelFlow
    import kotlinx.coroutines.sync.Mutex
    import kotlinx.coroutines.sync.withLock
    import kotlin.time.Clock
    import kotlinx.serialization.json.JsonElement
    -->
    ```kotlin
    class MyAgentSession(
        override val sessionId: SessionId,
        private val promptExecutor: PromptExecutor,
        private val protocol: Protocol,
        private val clock: Clock
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
    
            // Ensure only one agent session runs at a time
            agentMutex.withLock {
                val agent = AIAgent(
                    promptExecutor = promptExecutor,
                    agentConfig = agentConfig
                ) {
                    install(AcpAgent) {
                        this.sessionId = this@MyAgentSession.sessionId.value
                        this.protocol = this@MyAgentSession.protocol
                        this.eventsProducer = this@channelFlow
                        this.setDefaultNotifications = true
                    }
                }

                agentJob = async { agent.run("Hello. How can you help me?") }
                agentJob?.await()
            }
        }
    
        private fun Prompt.appendPrompt(content: List<ContentBlock>): Prompt {
            return withMessages { messages ->
                messages + listOf(content.toKoogMessage(clock))
            }
        }
    
        override suspend fun cancel() {
            agentJob?.cancel()
        }
    }
    ```
    <!--- KNIT example-agent-client-protocol-02.kt -->

=== "AgentSupport"

    <!--- INCLUDE
    import ai.koog.prompt.executor.model.PromptExecutor
    import com.agentclientprotocol.agent.AgentInfo
    import com.agentclientprotocol.agent.AgentSession
    import com.agentclientprotocol.agent.AgentSupport
    import com.agentclientprotocol.client.ClientInfo
    import com.agentclientprotocol.common.Event
    import com.agentclientprotocol.common.SessionCreationParameters
    import com.agentclientprotocol.model.AgentCapabilities
    import com.agentclientprotocol.model.ContentBlock
    import com.agentclientprotocol.model.LATEST_PROTOCOL_VERSION
    import com.agentclientprotocol.model.PromptCapabilities
    import com.agentclientprotocol.model.SessionId
    import com.agentclientprotocol.protocol.Protocol
    import kotlinx.coroutines.flow.Flow
    import kotlinx.serialization.json.JsonElement
    import kotlin.time.Clock
    import kotlin.uuid.ExperimentalUuidApi
    import kotlin.uuid.Uuid
    class MyAgentSession(
        override val sessionId: SessionId,
        private val promptExecutor: PromptExecutor,
        private val protocol: Protocol,
        private val clock: Clock
    ): AgentSession {
        override suspend fun prompt(
            content: List<ContentBlock>,
            _meta: JsonElement?
        ): Flow<Event> {
            TODO("Not yet implemented")
        }
    }
    -->
    ```kotlin
    class MyAgentSupport(
        private val promptExecutor: PromptExecutor,
        private val clock: Clock,
        private val protocol: Protocol,
    ) : AgentSupport {
    
        override suspend fun initialize(clientInfo: ClientInfo): AgentInfo {
            return AgentInfo(
                protocolVersion = LATEST_PROTOCOL_VERSION,
                capabilities = AgentCapabilities(
                    loadSession = false, // Set to true if you implement session persistence
                    promptCapabilities = PromptCapabilities(
                        audio = false,
                        image = false,
                        embeddedContext = true
                    )
                )
            )
        }
    
        @OptIn(ExperimentalUuidApi::class)
        override suspend fun createSession(sessionParameters: SessionCreationParameters): AgentSession {
            val sessionId = SessionId(Uuid.random().toString())
            return MyAgentSession(sessionId, promptExecutor, protocol, clock)
        }
    
        override suspend fun loadSession(sessionId: SessionId, sessionParameters: SessionCreationParameters): AgentSession {
            throw UnsupportedOperationException("Session loading not implemented")
        }
    }
    ```
    <!--- KNIT example-agent-client-protocol-03.kt -->

## Event streaming

The `AgentSession` from the example defines a `prompt()` function that returns a `channelFlow` of events.
You then install the `AcpAgent` feature with `this@channelFlow` as `eventsProducer`.
This allows sending events from different coroutines.

## Execution synchronization

The `AgentSession` from the example uses a mutex to synchronize access to the agent instance
because ACP should not trigger a new agent execution until the previous one finishes.
For this, creating and running the agent happens in the scope of `withLock` for the defined mutex.

You also run the agent asynchronously within the `channelFlow` scope
as a deferred job `agentJob` to ensure that the agent is not cancelled prematurely.

## Handling ACP client input

ACP clients send user input as a list of [`ContentBlock`](https://agentclientprotocol.com/protocol/schema#contentblock) objects.
To process these in Koog, use the `List<ContentBlock>.toKoogMessage()` extension function
to convert ACP content blocks to [`Message.User`](api:prompt-model::ai.koog.prompt.message.Message.User)
and append it to your [agent's prompt](prompts/index.md).

The `AgentSession` from the example defines a private function to extend the initial agent prompt in an ACP session:

<!--- INCLUDE
import ai.koog.agents.features.acp.toKoogMessage
import ai.koog.prompt.dsl.Prompt
import com.agentclientprotocol.model.ContentBlock
import kotlin.time.Clock

val clock: Clock = Clock.System
-->
```kotlin
private fun Prompt.appendPrompt(content: List<ContentBlock>): Prompt {
    return withMessages { messages ->
        messages + listOf(content.toKoogMessage(clock))
    }
}
```
<!--- KNIT example-agent-client-protocol-04.kt -->

!!! note

    A `Clock` instance is required to timestamp the message.

For more information, see [Converting messages](#converting-messages).

## Converting messages

The `agents-features-acp` module provides extension functions
to seamlessly convert between Koog's internal message types
and [ACP content blocks](https://agentclientprotocol.com/protocol/content).

Use the following functions when receiving input from an ACP client:

- `List<ContentBlock>.toKoogMessage()` converts a list of ACP content blocks to [`Message.User`](api:prompt-model::ai.koog.prompt.message.Message.User)
- `ContentBlock.toKoogContentPart()` converts a single ACP content block to [`ContentPart`](api:prompt-model::ai.koog.prompt.message.ContentPart)

Use the following functions to construct ACP events or content blocks from Koog messages:

- `Message.Response.toAcpEvents()` converts a [`Message.Response`](api:prompt-model::ai.koog.prompt.message.Message.Response) to a list of ACP session update events
- `ContentPart.toAcpContentBlock()` converts a [`ContentPart`](api:prompt-model::ai.koog.prompt.message.ContentPart) to a single ACP content block

## Handling agent notifications

By default, `setDefaultNotifications` is set to `true`
and the ACP-enabled agent automatically handles the following notifications:

- **Agent completion**

    Sends `PromptResponseEvent` with `StopReason.END_TURN` when the agent completes successfully

- **Agent execution failures**

    Sends `PromptResponseEvent` with the appropriate stop reason:

    - `StopReason.MAX_TURN_REQUESTS` when the agent exceeds max iterations
    - `StopReason.REFUSAL` for other execution failures
  
- **LLM responses**

    Converts and sends LLM responses as ACP events (text, tool calls, reasoning)

- **Tool call lifecycle**

    Reports tool call status changes:

    - `ToolCallStatus.IN_PROGRESS` when a tool call starts
    - `ToolCallStatus.COMPLETED` when a tool call succeeds
    - `ToolCallStatus.FAILED` when a tool call fails

If you want to customize notification handling,
set `setDefaultNotifications = false` and process agent events according to the specification.

## Sending custom events

Besides automatic notifications,
you can send custom events to the ACP client at any point during the agent execution
using `sendEvent` within the `withAcpAgent` block.
This is useful for progress updates, custom status messages, or plan updates.

You can do this inside an `AIAgentContext`, for example, in a node:

<!--- INCLUDE
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.features.acp.withAcpAgent
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.model.Plan
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.protocol.sendRequest
-->
```kotlin
val plan: Plan = TODO()

val strategy = strategy<Unit, Unit>("my-strategy") {
    val node by node<Unit, Unit> {
        withAcpAgent {
            sendEvent(
                Event.SessionUpdateEvent(
                    SessionUpdate.PlanUpdate(plan.entries)
                )
            )
        }
    }
}
```
<!--- KNIT example-agent-client-protocol-05.kt -->

You can also access the underlying `protocol` to send custom requests to the client, such as authentication requests:

<!--- INCLUDE
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.features.acp.withAcpAgent
import com.agentclientprotocol.model.AcpMethod
import com.agentclientprotocol.model.AuthMethodId
import com.agentclientprotocol.model.AuthenticateRequest
import com.agentclientprotocol.protocol.sendRequest
-->
```kotlin
val strategy = strategy<Unit, Unit>("my-strategy") {
    val node by node<Unit, Unit> {
        withAcpAgent {
            protocol.sendRequest(
                AcpMethod.AgentMethods.Authenticate,
                AuthenticateRequest(methodId = AuthMethodId("Google"))
            )
        }
    }
}
```
<!--- KNIT example-agent-client-protocol-06.kt -->

## Examples

You can find working examples of Koog agents in the Koog repository under [/examples](https://github.com/JetBrains/koog/tree/develop/examples/).

### Running a console-based ACP client

This example runs a console-based ACP client that interacts with a simple Koog agent.

1. Open [/examples/simple-examples](https://github.com/JetBrains/koog/blob/develop/examples/simple-examples/).
2. See the [README](https://github.com/JetBrains/koog/blob/develop/examples/simple-examples/README.md)
   for information about configuring your API key for an LLM provider.
3. Run the `runExampleAcpApp` Gradle task.
4. When the ACP client starts in the console, type a request for the agent, like:
    ```text
    List files in the current directory and create a new file named 'acp-test.txt' with the content 'Hello from ACP!'.
    ```
5. Observe the event traces in the console,
   which show how Koog events are converted to ACP events and sent to the client.

### Connecting an ACP-enabled Koog agent to a JetBrains IDE

This example demonstrates how to create an ACP-enabled agent and connect to IntelliJ IDEA.

1. Open [/examples/acp-agent](https://github.com/JetBrains/koog/tree/develop/examples/acp-agent)
2. Run the `installDist` Gradle task.
3. This should create the agent executable: `build/install/acp-agent/bin/acp-agent`
   (`acp-agent.bat` for Windows).
4. Open IntelliJ IDEA (or another JetBrains IDE).
5. Go to **AI Chat** > **Options** > **Add Custom Agent**.
6. In the opened `acp.json` file, paste the following:

    ```json
    {
        "agent_servers": {
            "Koog Agent": {
                "command": "/absolute/path/to/acp-agent/build/install/acp-agent/bin/acp-agent",
                "args": [],
                "env": {
                    "OPENAI_API_KEY": "paste-your-api-key-here"
                }
            }
        }
    }
    ```

    Configuration parameters:

    - `agent_servers`: Object containing one or more agent configurations
    - `Koog Agent`: Display name shown in IDE's agent selector
    - `command`: Absolute path to the agent executable
    - `args`: Command-line arguments (empty for this agent)
    - `env`: Environment variables passed to the agent process (OpenAI API key in this example)

7. The agent should become available in the **AI Chat** tool window.

For more information about adding custom agents to your IDE,
see [AI Assistant documentation](https://www.jetbrains.com/help/ai-assistant/acp.html#add-custom-agent)
and [this blog post](https://blog.jetbrains.com/ai/2026/02/koog-x-acp-connect-an-agent-to-your-ide-and-more/).


[Agent Client Protocol]: https://agentclientprotocol.com
[ACP Kotlin SDK]: https://github.com/agentclientprotocol/kotlin-sdk
