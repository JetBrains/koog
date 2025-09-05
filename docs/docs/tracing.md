# Tracing

This page includes details about the Tracing feature, which provides comprehensive tracing capabilities for AI agents.

## Feature overview

The Tracing feature is a powerful monitoring and debugging tool that captures detailed information about agent runs,
including:

- Strategy execution
- LLM calls
- Tool invocations
- Node execution within the agent graph

This feature operates by intercepting key events in the agent pipeline and forwarding them to configurable message
processors. These processors can output the trace information to various destinations such as log files or other types
of files in the filesystem, enabling developers to gain insights into agent behavior and troubleshoot issues effectively.

### Event flow

1. The Tracing feature intercepts events in the agent pipeline.
2. Events are filtered based on the configured message filter.
3. Filtered events are passed to registered message processors.
4. Message processors format and output the events to their respective destinations.

## Configuration and initialization

### Basic setup

To use the Tracing feature, you need to:

1. Have one or more message processors (you can use the existing ones or create your own).
2. Install `Tracing` in your agent.
3. Configure the message filter (optional).
4. Add the message processors to the feature.

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.feature.model.AfterLLMCallEvent
import ai.koog.agents.core.feature.model.ToolCallEvent
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageFileWriter
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageLogWriter
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.llm.OllamaModels
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
-->
```kotlin
// Defining a logger/file that will be used as a destination of trace messages 
val logger = KotlinLogging.logger { }
val outputPath = Path("/path/to/trace.log")

// Creating an agent
val agent = AIAgent(
   executor = simpleOllamaAIExecutor(),
   llmModel = OllamaModels.Meta.LLAMA_3_2,
) {
   install(Tracing) {
      // Configure message processors to handle trace events
      addMessageProcessor(TraceFeatureMessageLogWriter(logger))
      addMessageProcessor(
         TraceFeatureMessageFileWriter(
            outputPath,
            { path: Path -> SystemFileSystem.sink(path).buffered() }
         )
      )

      // Optionally filter messages
      messageFilter = { message ->
         // Only trace LLM calls and tool calls
         message is AfterLLMCallEvent || message is ToolCallEvent
      }
   }
}
```
<!--- KNIT example-tracing-01.kt -->

### Message filtering

You can process all existing events or select some of them based on specific criteria.
The message filter lets you control which events are processed. This is useful for focusing on specific aspects of
agent runs:

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.feature.model.*
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.llm.OllamaModels

val agent = AIAgent(
   executor = simpleOllamaAIExecutor(),
   llmModel = OllamaModels.Meta.LLAMA_3_2,
) {
   install(Tracing) {
-->
<!--- SUFFIX
   }
}
-->
```kotlin
// Filter for LLM-related events only
messageFilter = { message -> 
    message is BeforeLLMCallEvent || message is AfterLLMCallEvent
}

// Filter for tool-related events only
messageFilter = { message -> 
    message is ToolCallEvent ||
           message is ToolCallResultEvent ||
           message is ToolValidationErrorEvent ||
           message is ToolCallFailureEvent
}

// Filter for node execution events only
messageFilter = { message -> 
    message is AIAgentNodeExecutionStartEvent || message is AIAgentNodeExecutionEndEvent
}
```
<!--- KNIT example-tracing-02.kt -->

### Large trace volumes

For agents with complex strategies or long-running executions, the volume of trace events can be substantial. Consider using the following methods to manage the volume of events:

- Use specific message filters to reduce the number of events.
- Implement custom message processors with buffering or sampling.
- Use file rotation for log files to prevent them from growing too large.

### Dependency graph

The Tracing feature has the following dependencies:

```
Tracing
├── AIAgentPipeline (for intercepting events)
├── TraceFeatureConfig
│   └── FeatureConfig
├── Message Processors
│   ├── TraceFeatureMessageLogWriter
│   │   └── FeatureMessageLogWriter
│   ├── TraceFeatureMessageFileWriter
│   │   └── FeatureMessageFileWriter
│   └── TraceFeatureMessageRemoteWriter
│       └── FeatureMessageRemoteWriter
└── Event Types (from ai.koog.agents.core.feature.model)
    ├── AIAgentStartedEvent
    ├── AIAgentFinishedEvent
    ├── AIAgentRunErrorEvent
    ├── AIAgentStrategyStartEvent
    ├── AIAgentStrategyFinishedEvent
    ├── AIAgentNodeExecutionStartEvent
    ├── AIAgentNodeExecutionEndEvent
    ├── LLMCallStartEvent
    ├── LLMCallWithToolsStartEvent
    ├── LLMCallEndEvent
    ├── LLMCallWithToolsEndEvent
    ├── ToolCallEvent
    ├── ToolValidationErrorEvent
    ├── ToolCallFailureEvent
    └── ToolCallResultEvent
```

## Examples and quickstarts

### Basic tracing to logger

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageLogWriter
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.llm.OllamaModels
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
-->
```kotlin
// Create a logger
val logger = KotlinLogging.logger { }

fun main() {
    runBlocking {
       // Create an agent with tracing
       val agent = AIAgent(
          executor = simpleOllamaAIExecutor(),
          llmModel = OllamaModels.Meta.LLAMA_3_2,
       ) {
          install(Tracing) {
             addMessageProcessor(TraceFeatureMessageLogWriter(logger))
          }
       }

       // Run the agent
       agent.run("Hello, agent!")
    }
}
```
<!--- KNIT example-tracing-03.kt -->


## Error handling and edge cases

### No message processors

If no message processors are added to the Tracing feature, a warning will be logged:

```
Tracing Feature. No feature out stream providers are defined. Trace streaming has no target.
```

The feature will still intercept events, but they will not be processed or output anywhere.

### Resource management

Message processors may hold resources (like file handles) that need to be properly released. Use the `use` extension
function to ensure proper cleanup:

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.example.exampleTracing01.outputPath
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageFileWriter
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.runBlocking
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

const val input = "What's the weather like in New York?"

fun main() {
   runBlocking {
-->
<!--- SUFFIX
   }
}
-->
```kotlin
// Creating an agent
val agent = AIAgent(
    executor = simpleOllamaAIExecutor(),
    llmModel = OllamaModels.Meta.LLAMA_3_2,
) {
    val writer = TraceFeatureMessageFileWriter(
        outputPath,
        { path: Path -> SystemFileSystem.sink(path).buffered() }
    )

    install(Tracing) {
        addMessageProcessor(writer)
    }
}
// Run the agent
agent.run(input)
// Writer will be automatically closed when the block exits
```
<!--- KNIT example-tracing-04.kt -->

### Tracing specific events to file


<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.feature.model.AfterLLMCallEvent
import ai.koog.agents.core.feature.model.BeforeLLMCallEvent
import ai.koog.agents.example.exampleTracing01.outputPath
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageFileWriter
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.runBlocking
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

const val input = "What's the weather like in New York?"

fun main() {
    runBlocking {
        // Creating an agent
        val agent = AIAgent(
            executor = simpleOllamaAIExecutor(),
            llmModel = OllamaModels.Meta.LLAMA_3_2,
        ) {
            val writer = TraceFeatureMessageFileWriter(
                outputPath,
                { path: Path -> SystemFileSystem.sink(path).buffered() }
            )
-->
<!--- SUFFIX
        }
    }
}
-->
```kotlin
install(Tracing) {
    // Only trace LLM calls
    messageFilter = { message ->
        message is BeforeLLMCallEvent || message is AfterLLMCallEvent
    }
    addMessageProcessor(writer)
}
```
<!--- KNIT example-tracing-05.kt -->

### Tracing specific events to remote endpoint

You use tracing to remote endpoints when you need to send event data via the network. Once initiated, tracing to a
remote endpoint launches a light server at the specified port number and sends events via Kotlin Server-Sent Events 
(SSE).

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.feature.remote.server.config.DefaultServerConnectionConfig
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageRemoteWriter
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.runBlocking

const val input = "What's the weather like in New York?"
const val port = 4991
const val host = "localhost"

fun main() {
   runBlocking {
-->
<!--- SUFFIX
   }
}
-->
```kotlin
// Creating an agent
val agent = AIAgent(
    executor = simpleOllamaAIExecutor(),
    llmModel = OllamaModels.Meta.LLAMA_3_2,
) {
    val connectionConfig = DefaultServerConnectionConfig(host = host, port = port)
    val writer = TraceFeatureMessageRemoteWriter(
        connectionConfig = connectionConfig
    )

    install(Tracing) {
        addMessageProcessor(writer)
    }
}
// Run the agent
agent.run(input)
// Writer will be automatically closed when the block exits
```
<!--- KNIT example-tracing-06.kt -->

On the client side, you can use `FeatureMessageRemoteClient` to receive events and deserialize them.

<!--- INCLUDE
import ai.koog.agents.core.feature.model.AIAgentFinishedEvent
import ai.koog.agents.core.feature.model.DefinedFeatureEvent
import ai.koog.agents.core.feature.remote.client.config.DefaultClientConnectionConfig
import ai.koog.agents.core.feature.remote.client.FeatureMessageRemoteClient
import ai.koog.agents.utils.use
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.consumeAsFlow

const val input = "What's the weather like in New York?"
const val port = 4991
const val host = "localhost"

fun main() {
   runBlocking {
-->
<!--- SUFFIX
   }
}
-->
```kotlin
val clientConfig = DefaultClientConnectionConfig(host = host, port = port, protocol = URLProtocol.HTTP)
val agentEvents = mutableListOf<DefinedFeatureEvent>()

val clientJob = launch {
    FeatureMessageRemoteClient(connectionConfig = clientConfig, scope = this).use { client ->
        val collectEventsJob = launch {
            client.receivedMessages.consumeAsFlow().collect { event ->
                // Collect events from server
                agentEvents.add(event as DefinedFeatureEvent)

                // Stop collecting events on angent finished
                if (event is AIAgentFinishedEvent) {
                    cancel()
                }
            }
        }
        client.connect()
        collectEventsJob.join()
        client.healthCheck()
    }
}

listOf(clientJob).joinAll()
```
<!--- KNIT example-tracing-07.kt -->

## API documentation

The Tracing feature follows a modular architecture with these key components:

1. [Tracing](https://api.koog.ai/agents/agents-features/agents-features-trace/ai.koog.agents.features.tracing.feature/-tracing/index.html): the main feature class that intercepts events in the agent pipeline.
2. [TraceFeatureConfig](https://api.koog.ai/agents/agents-features/agents-features-trace/ai.koog.agents.features.tracing.feature/-trace-feature-config/index.html): configuration class for customizing feature behavior.
3. Message Processors: components that process and output trace events:
    - [TraceFeatureMessageLogWriter](https://api.koog.ai/agents/agents-features/agents-features-trace/ai.koog.agents.features.tracing.writer/-trace-feature-message-log-writer/index.html): writes trace events to a logger.
    - [TraceFeatureMessageFileWriter](https://api.koog.ai/agents/agents-features/agents-features-trace/ai.koog.agents.features.tracing.writer/-trace-feature-message-file-writer/index.html): writes trace events to a file.
    - [TraceFeatureMessageRemoteWriter](https://api.koog.ai/agents/agents-features/agents-features-trace/ai.koog.agents.features.tracing.writer/-trace-feature-message-remote-writer/index.html): sends trace events to a remote server.

## FAQ and troubleshooting

The following section includes commonly asked questions and answers related to the Tracing feature. 

### How do I trace only specific parts of my agent's execution?

Use the `messageFilter` property to filter events. For example, to trace only node execution:

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.feature.model.AfterLLMCallEvent
import ai.koog.agents.core.feature.model.BeforeLLMCallEvent
import ai.koog.agents.example.exampleTracing01.outputPath
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageFileWriter
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.runBlocking
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

const val input = "What's the weather like in New York?"

fun main() {
    runBlocking {
        // Creating an agent
        val agent = AIAgent(
            executor = simpleOllamaAIExecutor(),
            llmModel = OllamaModels.Meta.LLAMA_3_2,
        ) {
            val writer = TraceFeatureMessageFileWriter(
                outputPath,
                { path: Path -> SystemFileSystem.sink(path).buffered() }
            )
-->
<!--- SUFFIX
        }
    }
}
-->
```kotlin
install(Tracing) {
   // Only trace LLM calls
   messageFilter = { message ->
      message is BeforeLLMCallEvent || message is AfterLLMCallEvent
   }
   addMessageProcessor(writer)
}
```
<!--- KNIT example-tracing-08.kt -->

### Can I use multiple message processors?

Yes, you can add multiple message processors to trace to different destinations simultaneously:

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.feature.remote.server.config.DefaultServerConnectionConfig
import ai.koog.agents.example.exampleTracing01.outputPath
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageFileWriter
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageLogWriter
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageRemoteWriter
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.llm.OllamaModels
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

const val input = "What's the weather like in New York?"
val syncOpener = { path: Path -> SystemFileSystem.sink(path).buffered() }
val logger = KotlinLogging.logger {}
val connectionConfig = DefaultServerConnectionConfig(host = ai.koog.agents.example.exampleTracing06.host, port = ai.koog.agents.example.exampleTracing06.port)

fun main() {
   runBlocking {
      // Creating an agent
      val agent = AIAgent(
         executor = simpleOllamaAIExecutor(),
         llmModel = OllamaModels.Meta.LLAMA_3_2,
      ) {
-->
<!--- SUFFIX
        }
    }
}
-->
```kotlin
install(Tracing) {
    addMessageProcessor(TraceFeatureMessageLogWriter(logger))
    addMessageProcessor(TraceFeatureMessageFileWriter(outputPath, syncOpener))
    addMessageProcessor(TraceFeatureMessageRemoteWriter(connectionConfig))
}
```
<!--- KNIT example-tracing-09.kt -->

### How can I create a custom message processor?

Implement the `FeatureMessageProcessor` interface:

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.feature.model.AIAgentNodeExecutionStartEvent
import ai.koog.agents.core.feature.model.AfterLLMCallEvent
import ai.koog.agents.core.feature.message.FeatureMessage
import ai.koog.agents.core.feature.message.FeatureMessageProcessor
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

fun main() {
   runBlocking {
      // Creating an agent
      val agent = AIAgent(
         executor = simpleOllamaAIExecutor(),
         llmModel = OllamaModels.Meta.LLAMA_3_2,
      ) {
-->
<!--- SUFFIX
        }
    }
}
-->
```kotlin
class CustomTraceProcessor : FeatureMessageProcessor() {

    // Current open state of the processor
    private var _isOpen = MutableStateFlow(false)

    override val isOpen: StateFlow<Boolean>
        get() = _isOpen.asStateFlow()
    
    override suspend fun processMessage(message: FeatureMessage) {
        // Custom processing logic
        when (message) {
            is AIAgentNodeExecutionStartEvent -> {
                // Process node start event
            }

            is AfterLLMCallEvent -> {
                // Process LLM call end event
           }
            // Handle other event types 
        }
    }

    override suspend fun close() {
        // Close connections of established
    }
}

// Use your custom processor
install(Tracing) {
    addMessageProcessor(CustomTraceProcessor())
}
```
<!--- KNIT example-tracing-10.kt -->

For more information about existing event types that can be handled by message processors, see [Predefined event types](#predefined-event-types).

## Predefined event types

Koog provides predefined event types that can be used in custom message processors. The predefined events can be
classified into several categories, depending on the entity they relate to:

- [Agent events](#agent-events)
- [Strategy events](#strategy-events)
- [Node events](#node-events)
- [LLM call events](#llm-call-events)
- [Tool call events](#tool-call-events)

### Agent events

#### AIAgentStartedEvent

Represents the start of an agent run. Includes the following fields:

| Name           | Data type | Required | Default               | Description                                                               |
|----------------|-----------|----------|-----------------------|---------------------------------------------------------------------------|
| `strategyName` | String    | Yes      |                       | The name of the strategy that the agent should follow.                    |
| `eventId`      | String    | No       | `AIAgentStartedEvent` | The identifier of the event. Usually the `simpleName` of the event class. |

#### AIAgentFinishedEvent

Represents the end of an agent run. Includes the following fields:

| Name           | Data type | Required | Default                | Description                                                               |
|----------------|-----------|----------|------------------------|---------------------------------------------------------------------------|
| `strategyName` | String    | Yes      |                        | The name of the strategy that the agent followed.                         |
| `result`       | String    | Yes      |                        | The result of the agent run. Can be `null` if there is no result.         |
| `eventId`      | String    | No       | `AIAgentFinishedEvent` | The identifier of the event. Usually the `simpleName` of the event class. |

#### AIAgentRunErrorEvent

Represents the occurrence of an error during an agent run. Includes the following fields:

| Name           | Data type    | Required | Default                | Description                                                                                                     |
|----------------|--------------|----------|------------------------|-----------------------------------------------------------------------------------------------------------------|
| `strategyName` | String       | Yes      |                        | The name of the strategy that the agent followed.                                                               |
| `error`        | AIAgentError | Yes      |                        | The specific error that occurred during the agent run. For more information, see [AIAgentError](#aiagenterror). |
| `eventId`      | String       | No       | `AIAgentRunErrorEvent` | The identifier of the event. Usually the `simpleName` of the event class.                                       |

<a id="aiagenterror"></a>
The `AIAgentError` class provides more details about an error that occurred during an agent run. Includes the following fields:

| Name         | Data type | Required | Default | Description                                                      |
|--------------|-----------|----------|---------|------------------------------------------------------------------|
| `message`    | String    | Yes      |         | The message that provides more details about the specific error. |
| `stackTrace` | String    | Yes      |         | The collection of stack records until the last executed code.    |
| `cause`      | String    | No       | null    | The cause of the error, if available.                            |

### Strategy events

#### AIAgentStrategyStartEvent

Represents the start of a strategy run. Includes the following fields:

| Name           | Data type | Required | Default                     | Description                                                               |
|----------------|-----------|----------|-----------------------------|---------------------------------------------------------------------------|
| `strategyName` | String    | Yes      |                             | The name of the strategy.                                                 |
| `eventId`      | String    | No       | `AIAgentStrategyStartEvent` | The identifier of the event. Usually the `simpleName` of the event class. |

#### AIAgentStrategyFinishedEvent

Represents the end of a strategy run. Includes the following fields:

| Name           | Data type | Required | Default                        | Description                                                               |
|----------------|-----------|----------|--------------------------------|---------------------------------------------------------------------------|
| `strategyName` | String    | Yes      |                                | The name of the strategy.                                                 |
| `result`       | String    | Yes      |                                | The result of the run.                                                    |
| `eventId`      | String    | No       | `AIAgentStrategyFinishedEvent` | The identifier of the event. Usually the `simpleName` of the event class. |

### Node events

#### AIAgentNodeExecutionStartEvent

Represents the start of a node run. Includes the following fields:

| Name       | Data type | Required | Default                          | Description                                                               |
|------------|-----------|----------|----------------------------------|---------------------------------------------------------------------------|
| `nodeName` | String    | Yes      |                                  | The name of the node whose run started.                                   |
| `input`    | String    | Yes      |                                  | The input value for the node.                                             |
| `eventId`  | String    | No       | `AIAgentNodeExecutionStartEvent` | The identifier of the event. Usually the `simpleName` of the event class. |

#### AIAgentNodeExecutionEndEvent

Represents the end of a node run. Includes the following fields:

| Name       | Data type | Required | Default                        | Description                                                               |
|------------|-----------|----------|--------------------------------|---------------------------------------------------------------------------|
| `nodeName` | String    | Yes      |                                | The name of the node whose run ended.                                     |
| `input`    | String    | Yes      |                                | The input value for the node.                                             |
| `output`   | String    | Yes      |                                | The output value produced by the node.                                    |
| `eventId`  | String    | No       | `AIAgentNodeExecutionEndEvent` | The identifier of the event. Usually the `simpleName` of the event class. |

### LLM call events

#### LLMCallStartEvent

Represents the start of an LLM call. Includes the following fields:

| Name      | Data type          | Required | Default             | Description                                                                        |
|-----------|--------------------|----------|---------------------|------------------------------------------------------------------------------------|
| `prompt`  | Prompt             | Yes      |                     | The prompt that is sent to the model. For more information, see [Prompt](#prompt). |
| `tools`   | List&lt;String&gt; | Yes      |                     | The list of tools that the model can call.                                         |
| `eventId` | String             | No       | `LLMCallStartEvent` | The identifier of the event. Usually the `simpleName` of the event class.          |

<a id="prompt"></a>
The `Prompt` class represents a data structure for a prompt, consisting of a list of messages, a unique identifier, and
optional parameters for language model settings. Includes the following fields:

| Name       | Data type           | Required | Default     | Description                                                  |
|------------|---------------------|----------|-------------|--------------------------------------------------------------|
| `messages` | List&lt;Message&gt; | Yes      |             | The list of messages that the prompt consists of.            |
| `id`       | String              | Yes      |             | The unique identifier for the prompt.                        |
| `params`   | LLMParams           | No       | LLMParams() | The settings that control the way the LLM generates content. |

#### LLMCallEndEvent

Represents the end of an LLM call. Includes the following fields:

| Name        | Data type                    | Required | Default           | Description                                                               |
|-------------|------------------------------|----------|-------------------|---------------------------------------------------------------------------|
| `responses` | List&lt;Message.Response&gt; | Yes      |                   | One or more responses returned by the model.                              |
| `eventId`   | String                       | No       | `LLMCallEndEvent` | The identifier of the event. Usually the `simpleName` of the event class. |

### Tool call events

#### ToolCallEvent

Represents the event of a model calling a tool. Includes the following fields:

| Name       | Data type | Required | Default         | Description                                                               |
|------------|-----------|----------|-----------------|---------------------------------------------------------------------------|
| `toolName` | String    | Yes      |                 | The name of the tool.                                                     |
| `toolArgs` | Tool.Args | Yes      |                 | The arguments that are provided to the tool.                              |
| `eventId`  | String    | No       | `ToolCallEvent` | The identifier of the event. Usually the `simpleName` of the event class. |

#### ToolValidationErrorEvent

Represents the occurrence of a validation error during a tool call. Includes the following fields:

| Name           | Data type | Required | Default                    | Description                                                               |
|----------------|-----------|----------|----------------------------|---------------------------------------------------------------------------|
| `toolName`     | String    | Yes      |                            | The name of the tool for which validation failed.                         |
| `toolArgs`     | Tool.Args | Yes      |                            | The arguments that are provided to the tool.                              |
| `errorMessage` | String    | Yes      |                            | The validation error message.                                             |
| `eventId`      | String    | No       | `ToolValidationErrorEvent` | The identifier of the event. Usually the `simpleName` of the event class. |

#### ToolCallFailureEvent

Represents a failure to call a tool. Includes the following fields:

| Name       | Data type    | Required | Default                | Description                                                                                                           |
|------------|--------------|----------|------------------------|-----------------------------------------------------------------------------------------------------------------------|
| `toolName` | String       | Yes      |                        | The name of the tool.                                                                                                 |
| `toolArgs` | Tool.Args    | Yes      |                        | The arguments that are provided to the tool.                                                                          |
| `error`    | AIAgentError | Yes      |                        | The specific error that occurred when trying to call a tool. For more information, see [AIAgentError](#aiagenterror). |
| `eventId`  | String       | No       | `ToolCallFailureEvent` | The identifier of the event. Usually the `simpleName` of the event class.                                             |

#### ToolCallResultEvent

Represents a successful tool call with the return of a result. Includes the following fields:

| Name       | Data type  | Required | Default               | Description                                                               |
|------------|------------|----------|-----------------------|---------------------------------------------------------------------------|
| `toolName` | String     | Yes      |                       | The name of the tool.                                                     |
| `toolArgs` | Tool.Args  | Yes      |                       | The arguments that are provided to the tool.                              |
| `result`   | ToolResult | Yes      |                       | The result of the tool call.                                              |
| `eventId`  | String     | No       | `ToolCallResultEvent` | The identifier of the event. Usually the `simpleName` of the event class. |
