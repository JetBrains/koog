# Agent events

Agent events are actions or interactions that occur as part of an agent workflow. They include:

- Agent lifecycle events
- Strategy events
- Node execution events
- LLM call events
- LLM streaming events
- Tool execution events

Note: Feature events are defined in the agents-core module and live under the package `ai.koog.agents.core.feature.model.events`. Features such as `agents-features-trace`, and `agents-features-event-handler` consume these events to process and forward messages created during agent execution.

## Predefined event types

Koog provides predefined event types that can be used in custom message processors. The predefined events can be
classified into several categories, depending on the entity they relate to:

- [Agent events](#agent-events)
- [Strategy events](#strategy-events)
- [Node events](#node-events)
- [Subgraph events](#subgraph-events)
- [LLM call events](#llm-call-events)
- [LLM streaming events](#llm-streaming-events)
- [Tool execution events](#tool-execution-events)

### Agent events

#### AgentStartingEvent

Represents the start of an agent run. Includes the following fields:

| Name       | Data type | Required | Default | Description                                             |
|------------|-----------|----------|---------|---------------------------------------------------------|
| `agentId`  | String    | Yes      |         | The unique identifier of the AI agent.                  |
| `runId`    | String    | Yes      |         | The unique identifier of the AI agent run.              |

#### AgentCompletedEvent

Represents the end of an agent run. Includes the following fields:

| Name       | Data type | Required | Default | Description                                                         |
|------------|-----------|----------|---------|---------------------------------------------------------------------|
| `agentId`  | String    | Yes      |         | The unique identifier of the AI agent.                              |
| `runId`    | String    | Yes      |         | The unique identifier of the AI agent run.                          |
| `result`   | String    | Yes      |         | The result of the agent run. Can be `null` if there is no result.   |

#### AgentExecutionFailedEvent

Represents the occurrence of an error during an agent run. Includes the following fields:

| Name      | Data type     | Required | Default | Description                                                                                                     |
|-----------|---------------|----------|---------|-----------------------------------------------------------------------------------------------------------------|
| `agentId` | String        | Yes      |         | The unique identifier of the AI agent.                                                                          |
| `runId`   | String        | Yes      |         | The unique identifier of the AI agent run.                                                                      |
| `error`   | AIAgentError  | Yes      |         | The specific error that occurred during the agent run. For more information, see [AIAgentError](#aiagenterror). |

#### AgentClosingEvent

Represents the closure or termination of an agent. Includes the following fields:

| Name      | Data type | Required | Default | Description                                             |
|-----------|-----------|----------|---------|---------------------------------------------------------|
| `agentId` | String    | Yes      |         | The unique identifier of the AI agent.                  |

<a id="aiagenterror"></a>
The `AIAgentError` class provides more details about an error that occurred during an agent run. Includes the following fields:

| Name         | Data type | Required | Default | Description                                                      |
|--------------|-----------|----------|---------|------------------------------------------------------------------|
| `message`    | String    | Yes      |         | The message that provides more details about the specific error. |
| `stackTrace` | String    | Yes      |         | The collection of stack records until the last executed code.    |
| `cause`      | String    | No       | null    | The cause of the error, if available.                            |

### Strategy events

#### GraphStrategyStartingEvent

Represents the start of a graph-based strategy run. Includes the following fields:

| Name            | Data type              | Required | Default | Description                                              |
|-----------------|------------------------|----------|---------|----------------------------------------------------------|
| `runId`         | String                 | Yes      |         | The unique identifier of the strategy run.               |
| `strategyName`  | String                 | Yes      |         | The name of the strategy.                                |
| `graph`         | StrategyEventGraph     | Yes      |         | The graph structure representing the strategy workflow.  |

#### FunctionalStrategyStartingEvent

Represents the start of a functional strategy run. Includes the following fields:

| Name            | Data type | Required | Default | Description                                |
|-----------------|-----------|----------|---------|--------------------------------------------|
| `runId`         | String    | Yes      |         | The unique identifier of the strategy run. |
| `strategyName`  | String    | Yes      |         | The name of the strategy.                  |

#### StrategyCompletedEvent

Represents the end of a strategy run. Includes the following fields:

| Name           | Data type | Required | Default | Description                                                                    |
|----------------|-----------|----------|---------|--------------------------------------------------------------------------------|
| `runId`        | String    | Yes      |         | The unique identifier of the strategy run.                                     |
| `strategyName` | String    | Yes      |         | The name of the strategy.                                                      |
| `result`       | String    | Yes      |         | The result of the run. Can be `null` if there is no result.                    |

### Node events

#### NodeExecutionStartingEvent

Represents the start of a node run. Includes the following fields:

| Name       | Data type   | Required | Default | Description                                      |
|------------|-------------|----------|---------|--------------------------------------------------|
| `runId`    | String      | Yes      |         | The unique identifier of the strategy run.       |
| `nodeName` | String      | Yes      |         | The name of the node whose run started.          |
| `input`    | JsonElement | No       | null    | The input value for the node.                    |

#### NodeExecutionCompletedEvent

Represents the end of a node run. Includes the following fields:

| Name       | Data type   | Required | Default | Description                                      |
|------------|-------------|----------|---------|--------------------------------------------------|
| `runId`    | String      | Yes      |         | The unique identifier of the strategy run.       |
| `nodeName` | String      | Yes      |         | The name of the node whose run ended.            |
| `input`    | JsonElement | No       | null    | The input value for the node.                    |
| `output`   | JsonElement | No       | null    | The output value produced by the node.           |

#### NodeExecutionFailedEvent

Represents an error that occurred during a node run. Includes the following fields:

| Name       | Data type    | Required | Default | Description                                                                                                     |
|------------|--------------|----------|---------|-----------------------------------------------------------------------------------------------------------------|
| `runId`    | String       | Yes      |         | The unique identifier of the strategy run.                                                                      |
| `nodeName` | String       | Yes      |         | The name of the node where the error occurred.                                                                  |
| `input`    | JsonElement  | No       | null    | The input data provided to the node.                                                                            |
| `error`    | AIAgentError | Yes      |         | The specific error that occurred during the node run. For more information, see [AIAgentError](#aiagenterror). |

### Subgraph events

#### SubgraphExecutionStartingEvent

Represents the start of a subgraph run. Includes the following fields:

| Name            | Data type   | Required | Default | Description                                         |
|-----------------|-------------|----------|---------|-----------------------------------------------------|
| `runId`         | String      | Yes      |         | The unique identifier of the strategy run.          |
| `subgraphName`  | String      | Yes      |         | The name of the subgraph whose run started.         |
| `input`         | JsonElement | No       | null    | The input value for the subgraph.                   |

#### SubgraphExecutionCompletedEvent

Represents the end of a subgraph run. Includes the following fields:

| Name            | Data type   | Required | Default | Description                                         |
|-----------------|-------------|----------|---------|-----------------------------------------------------|
| `runId`         | String      | Yes      |         | The unique identifier of the strategy run.          |
| `subgraphName`  | String      | Yes      |         | The name of the subgraph whose run ended.           |
| `input`         | JsonElement | No       | null    | The input value for the subgraph.                   |
| `output`        | JsonElement | No       | null    | The output value produced by the subgraph.          |

#### SubgraphExecutionFailedEvent

Represents an error that occurred during a subgraph run. Includes the following fields:

| Name            | Data type    | Required | Default | Description                                                                                                     |
|-----------------|--------------|----------|---------|-----------------------------------------------------------------------------------------------------------------|
| `runId`         | String       | Yes      |         | The unique identifier of the strategy run.                                                                      |
| `subgraphName`  | String       | Yes      |         | The name of the subgraph where the error occurred.                                                              |
| `input`         | JsonElement  | No       | null    | The input data provided to the subgraph.                                                                        |
| `error`         | AIAgentError | Yes      |         | The specific error that occurred during the subgraph run. For more information, see [AIAgentError](#aiagenterror). |

### LLM call events

#### LLMCallStartingEvent

Represents the start of an LLM call. Includes the following fields:

| Name     | Data type    | Required | Default | Description                                                                        |
|----------|--------------|----------|---------|------------------------------------------------------------------------------------|
| `runId`  | String       | Yes      |         | The unique identifier of the LLM run.                                              |
| `callId` | String       | Yes      |         | The unique identifier of the LLM Call, correlating the related events.             |
| `prompt` | Prompt       | Yes      |         | The prompt that is sent to the model. For more information, see [Prompt](#prompt). |
| `model`  | ModelInfo    | Yes      |         | The model information. See [ModelInfo](#modelinfo).                    |
| `tools`  | List<String> | Yes      |         | The list of tools that the model can call.                                         |

<a id="prompt"></a>
The `Prompt` class represents a data structure for a prompt, consisting of a list of messages, a unique identifier, and
optional parameters for language model settings. Includes the following fields:

| Name       | Data type           | Required | Default     | Description                                                  |
|------------|---------------------|----------|-------------|--------------------------------------------------------------|
| `messages` | List<Message>       | Yes      |             | The list of messages that the prompt consists of.            |
| `id`       | String              | Yes      |             | The unique identifier for the prompt.                        |
| `params`   | LLMParams           | No       | LLMParams() | The settings that control the way the LLM generates content. |

<a id="modelinfo"></a>
The `ModelInfo` class represents information about a language model, including its provider, model identifier, and characteristics. Includes the following fields:

| Name              | Data type | Required | Default | Description                                                              |
|-------------------|-----------|----------|---------|--------------------------------------------------------------------------|
| `provider`        | String    | Yes      |         | The provider identifier (e.g., "openai", "google", "anthropic").         |
| `model`           | String    | Yes      |         | The model identifier (e.g., "gpt-4", "claude-3").                        |
| `displayName`     | String    | No       | null    | Optional human-readable display name for the model.                      |
| `contextLength`   | Long      | No       | null    | Maximum number of tokens the model can process.                          |
| `maxOutputTokens` | Long      | No       | null    | Maximum number of tokens the model can generate.                         |

#### LLMCallCompletedEvent

Represents the end of an LLM call. Includes the following fields:

| Name                 | Data type              | Required | Default | Description                                                            |
|----------------------|------------------------|----------|---------|------------------------------------------------------------------------|
| `runId`              | String                 | Yes      |         | The unique identifier of the LLM run.                                  |
| `callId`             | String                 | Yes      |         | The unique identifier of the LLM Call, correlating the related events. |
| `prompt`             | Prompt                 | Yes      |         | The prompt used in the call.                                           |
| `model`              | ModelInfo              | Yes      |         | The model information. See [ModelInfo](#modelinfo).                    |
| `responses`          | List<Message.Response> | Yes      |         | One or more responses returned by the model.                           |
| `moderationResponse` | ModerationResult       | No       | null    | The moderation response, if any.                                       |

### LLM streaming events

#### LLMStreamingStartingEvent

Represents the start of an LLM streaming call. Includes the following fields:

| Name     | Data type    | Required | Default | Description                                                            |
|----------|--------------|----------|---------|------------------------------------------------------------------------|
| `runId`  | String       | Yes      |         | The unique identifier of the LLM run.                                  |
| `callId` | String       | Yes      |         | The unique identifier of the LLM Call, correlating the related events. |
| `prompt` | Prompt       | Yes      |         | The prompt that is sent to the model.                                  |
| `model`  | ModelInfo    | Yes      |         | The model information. See [ModelInfo](#modelinfo).                    |
| `tools`  | List<String> | Yes      |         | The list of tools that the model can call.                             |

#### LLMStreamingFrameReceivedEvent

Represents a streaming frame received from the LLM. Includes the following fields:

| Name    | Data type   | Required | Default | Description                                                             |
|---------|-------------|----------|---------|-------------------------------------------------------------------------|
| `runId` | String      | Yes      |         | The unique identifier of the LLM run.                                   |
| `callId`| String      | Yes      |         | The unique identifier of the LLM Call, correlating the related events.  |
| `frame` | StreamFrame | Yes      |         | The frame received from the stream.                                     |

#### LLMStreamingFailedEvent

Represents the occurrence of an error during an LLM streaming call. Includes the following fields:

| Name    | Data type    | Required | Default | Description                                                                                                 |
|---------|--------------|----------|---------|-------------------------------------------------------------------------------------------------------------|
| `runId` | String       | Yes      |         | The unique identifier of the LLM run.                                                                       |
| `callId`| String       | Yes      |         | The unique identifier of the LLM Call, correlating the related events.                                      |
| `error` | AIAgentError | Yes      |         | The specific error that occurred during streaming. For more information, see [AIAgentError](#aiagenterror). |

#### LLMStreamingCompletedEvent

Represents the end of an LLM streaming call. Includes the following fields:

| Name     | Data type    | Required | Default | Description                                                             |
|----------|--------------|----------|---------|-------------------------------------------------------------------------|
| `runId`  | String       | Yes      |         | The unique identifier of the LLM run.                                   |
| `callId` | String       | Yes      |         | The unique identifier of the LLM Call, correlating the related events.  |
| `prompt` | Prompt       | Yes      |         | The prompt that is sent to the model.                                   |
| `model`  | ModelInfo    | Yes      |         | The model information. See [ModelInfo](#modelinfo).                     |
| `tools`  | List<String> | Yes      |         | The list of tools that the model can call.                              |

### Tool execution events

#### ToolExecutionStartingEvent

Represents the event of a model calling a tool. Includes the following fields:

| Name          | Data type   | Required | Default | Description                                             |
|---------------|-------------|----------|---------|---------------------------------------------------------|
| `runId`       | String      | Yes      |         | The unique identifier of the strategy/agent run.        |
| `toolCallId`  | String      | No       | null    | The identifier of the tool call, if available.          |
| `toolName`    | String      | Yes      |         | The name of the tool.                                   |
| `toolArgs`    | JsonObject  | Yes      |         | The arguments that are provided to the tool.            |

#### ToolValidationFailedEvent

Represents the occurrence of a validation error during a tool call. Includes the following fields:

| Name          | Data type   | Required | Default | Description                                             |
|---------------|-------------|----------|---------|---------------------------------------------------------|
| `runId`       | String      | Yes      |         | The unique identifier of the strategy/agent run.        |
| `toolCallId`  | String      | No       | null    | The identifier of the tool call, if available.          |
| `toolName`    | String      | Yes      |         | The name of the tool for which validation failed.       |
| `toolArgs`    | JsonObject  | Yes      |         | The arguments that are provided to the tool.            |
| `error`       | String      | Yes      |         | The validation error message.                           |

#### ToolExecutionFailedEvent

Represents a failure to execute a tool. Includes the following fields:

| Name          | Data type    | Required | Default | Description                                                                                                             |
|---------------|--------------|----------|---------|-------------------------------------------------------------------------------------------------------------------------|
| `runId`       | String       | Yes      |         | The unique identifier of the strategy/agent run.                                                                        |
| `toolCallId`  | String       | No       | null    | The identifier of the tool call, if available.                                                                          |
| `toolName`    | String       | Yes      |         | The name of the tool.                                                                                                   |
| `toolArgs`    | JsonObject   | Yes      |         | The arguments that are provided to the tool.                                                                            |
| `error`       | AIAgentError | Yes      |         | The specific error that occurred when trying to call a tool. For more information, see [AIAgentError](#aiagenterror).   |

#### ToolExecutionCompletedEvent

Represents a successful tool call with the return of a result. Includes the following fields:

| Name          | Data type  | Required | Default | Description                              |
|---------------|------------|----------|---------|------------------------------------------|
| `runId`       | String     | Yes      |         | The unique identifier of the run.        |
| `toolCallId`  | String     | No       | null    | The identifier of the tool call.         |
| `toolName`    | String     | Yes      |         | The name of the tool.                    |
| `toolArgs`    | JsonObject | Yes      |         | The arguments provided to the tool.      |
| `result`      | String     | Yes      |         | The result of the tool call (nullable).  |

## FAQ and troubleshooting

The following section includes commonly asked questions and answers related to the Tracing feature.

### How do I trace only specific parts of my agent's execution?

Use the `messageFilter` property to filter events. For example, to trace only node execution:

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.feature.model.events.LLMCallCompletedEvent
import ai.koog.agents.core.feature.model.events.LLMCallStartingEvent
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
            promptExecutor = simpleOllamaAIExecutor(),
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
    val fileWriter = TraceFeatureMessageFileWriter(
        outputPath, 
        { path: Path -> SystemFileSystem.sink(path).buffered() }
    )
    addMessageProcessor(fileWriter)
    
    // Only trace LLM calls
    fileWriter.setMessageFilter { message ->
        message is LLMCallStartingEvent || message is LLMCallCompletedEvent
    }
}
```
<!--- KNIT example-events-01.kt -->

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
            promptExecutor = simpleOllamaAIExecutor(),
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
<!--- KNIT example-events-02.kt -->

### How can I create a custom message processor?

Implement the `FeatureMessageProcessor` interface:

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.feature.model.events.NodeExecutionStartingEvent
import ai.koog.agents.core.feature.model.events.LLMCallCompletedEvent
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
            promptExecutor = simpleOllamaAIExecutor(),
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
            is NodeExecutionStartingEvent -> {
                // Process node start event
            }

            is LLMCallCompletedEvent -> {
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
<!--- KNIT example-events-03.kt -->

For more information about existing event types that can be handled by message processors, see [Predefined event types](#predefined-event-types).
