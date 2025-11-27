# Custom features

Features provide a way to extend and enhance the functionality of AI agents at runtime. They are designed to be modular
and composable, allowing you to mix and match them according to your needs.

In addition to features that are available in Koog out of the box, you can also implement your own features by
extending a proper feature interface. This page presents the basic building blocks for your own feature using the
current Koog APIs.

## Feature interfaces

Koog provides two interfaces that you can extend to implement custom features:

- `AIAgentGraphFeature`: Represents a feature specific to [agents that have defined workflows](complex-workflow-agents.md) (graph-based agents).
- `AIAgentFunctionalFeature`: Represents a feature that can be used with [functional agents](functional-agents.md).

!!! note
    To create a custom feature that can be installed in both graph-based and functional agents, you need to extend both 
    interfaces.

## Implementing custom features

To implement a custom feature, you need to create a feature structure according to the following steps:

1. Create a feature class.
2. Define a configuration class.
3. Create a companion object that implements `AIAgentGraphFeature`, `AIAgentFunctionalFeature`, or both.
4. Give your feature a stable storage key so it can be retrieved in contexts.
5. Implement the required methods.

The code sample below shows the general pattern for implementing a custom feature that can be installed in both graph-based and functional agents:

<!--- INCLUDE
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.feature.AIAgentFunctionalFeature
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.pipeline.AIAgentFunctionalPipeline
import ai.koog.agents.core.feature.pipeline.AIAgentGraphPipeline
-->
```kotlin
class MyFeature(val someProperty: String) {
    class Config : FeatureConfig() {
        var configProperty: String = "default"
    }

    companion object Feature : AIAgentGraphFeature<Config, MyFeature>, AIAgentFunctionalFeature<Config, MyFeature> {
        // Stable storage key for retrieval in contexts
        override val key = createStorageKey<MyFeature>("my-feature")
        override fun createInitialConfig(): Config = Config()

        // Feature installation for graph-based agents
        override fun install(config: Config, pipeline: AIAgentGraphPipeline) : MyFeature {
            val feature = MyFeature(config.configProperty)

            pipeline.interceptAgentStarting(this) { context ->
                // Method implementation
            }
            return feature
        }

        // Feature installation for functional agents
        override fun install(config: Config, pipeline: AIAgentFunctionalPipeline) : MyFeature {
            val feature = MyFeature(config.configProperty)

            pipeline.interceptAgentStarting(this) { context ->
                // Method implementation
            }
            return feature
        }
    }
}
```
<!--- KNIT example-custom-features-01.kt -->

When creating an agent, install your feature using the `install` method:

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.agents.features.tracing.feature.Tracing

val MyFeature = Tracing
var configProperty = ""
-->
```kotlin
val agent = AIAgent(
    promptExecutor = simpleOpenAIExecutor(System.getenv("YOUR_API_KEY")),
    systemPrompt = "You are a helpful assistant. Answer user questions concisely.",
    llmModel = OpenAIModels.Chat.GPT4o
) {
    install(MyFeature) {
        configProperty = "value"
    }
}
```
<!--- KNIT example-custom-features-02.kt -->

### Pipeline interceptors

Interceptors represent various points in the agent lifecycle where you can hook into the agent execution pipeline to
implement your custom logic. Koog includes a range of predefined interceptors that you can use to observe various 
events.

Below are the interceptors that you can register from your feature’s `install` method. The listed interceptors are
grouped by type and apply both the graph-based and functional agent pipelines. To reduce noise and optimize cost when
developing actual features, register only the interceptors you need for the feature.

Agent and environment lifecycle:

- `interceptEnvironmentCreated`: Transform the agent environment when it’s created.
- `interceptAgentStarting`: Invoked when an agent run starts.
- `interceptAgentCompleted`: Invoked when an agent run completes successfully.
- `interceptAgentExecutionFailed`: Invoked when an agent run fails.
- `interceptAgentClosing`: Invoked just before the agent run closes (cleanup point).

Strategy lifecycle: 

- `interceptStrategyStarting`: Strategy begins execution.
- `interceptStrategyCompleted`: Strategy finishes execution.

LLM call lifecycle:

- `interceptLLMCallStarting`: Before an LLM call.
- `interceptLLMCallCompleted`: After an LLM call.

LLM streaming lifecycle:

- `interceptLLMStreamingStarting`: Before streaming starts.
- `interceptLLMStreamingFrameReceived`: For each received stream frame.
- `interceptLLMStreamingFailed`: When streaming fails.
- `interceptLLMStreamingCompleted`: After streaming completes.

Tool call lifecycle:

- `interceptToolCallStarting`: Before a tool is invoked.
- `interceptToolValidationFailed`: When tool input validation fails.
- `interceptToolCallFailed`: When the tool execution fails.
- `interceptToolCallCompleted`: After the tool completes (with a result).

#### Interceptors specific to graph-based agents

The following interceptors are available only on `AIAgentGraphPipeline` and let you observe node and subgraph lifecycle events.

Node execution lifecycle:

- `interceptNodeExecutionStarting`: Before a node starts executing.
- `interceptNodeExecutionCompleted`: After a node finishes executing.
- `interceptNodeExecutionFailed`: When a node execution fails with an error.

Subgraph execution lifecycle:

- `interceptSubgraphExecutionStarting`: Before a subgraph starts.
- `interceptSubgraphExecutionCompleted`: After a subgraph completes.
- `interceptSubgraphExecutionFailed`: When a subgraph execution fails.

Note that interceptors are feature-scoped: only the feature that registers a handler receives those events (subject to 
any `setEventFilter` you configure in your `FeatureConfig`).

### Disabling event filtering for a feature

Some features, such as debugger and OpenTelemetry, must observe the entire event stream. If your feature depends on the 
full event stream, disable event filtering by overriding `setEventFilter` in your feature configuration to ignore custom
filters and allow all events:

<!--- INCLUDE
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.handler.AgentLifecycleEventContext
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}
-->
```kotlin
class MyFeatureConfig : FeatureConfig() {
    override fun setEventFilter(filter: (AgentLifecycleEventContext) -> Boolean) {
        logger.warn { "Events filtering is not allowed for MyFeature." }
        super.setEventFilter { true }
    }
}
```
<!--- KNIT example-custom-features-03.kt -->

## Example: A basic logging feature

The following example shows how to implement a basic logging feature that logs agent lifecycle events. The example

<!--- INCLUDE
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.feature.AIAgentFunctionalFeature
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.pipeline.AIAgentFunctionalPipeline
import ai.koog.agents.core.feature.pipeline.AIAgentGraphPipeline
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging

val logger = KotlinLogging.logger {}
-->
```kotlin
class LoggingFeature(private val logger: KLogger) {
    class Config : FeatureConfig() {
        var loggerName: String = "agent-logs"
    }

    companion object Feature : AIAgentGraphFeature<Config, LoggingFeature>, AIAgentFunctionalFeature<Config, LoggingFeature> {
        override val key = createStorageKey<LoggingFeature>("logging-feature")
        override fun createInitialConfig(): Config = Config()

        override fun install(config: Config, pipeline: AIAgentGraphPipeline) : LoggingFeature {
            val logging = LoggingFeature(logger = logger)

            pipeline.interceptAgentStarting(this) { e ->
                logger.info { "Agent starting: runId=${e.runId}" }
            }
            pipeline.interceptStrategyStarting(this) { _ ->
                logger.info { "Strategy starting" }
            }
            pipeline.interceptNodeExecutionStarting(this) { e ->
                logger.info { "Node ${e.node.name} input: ${e.input}" }
            }
            pipeline.interceptNodeExecutionCompleted(this) { e ->
                logger.info { "Node ${e.node.name} output: ${e.output}" }
            }
            pipeline.interceptLLMCallStarting(this) { e ->
                logger.info { "Making LLM call with ${e.tools.size} tools" }
            }
            pipeline.interceptLLMCallCompleted(this) { e ->
                logger.info { "Received ${e.responses.size} response(s)" }
            }
            return logging
        }

        override fun install(config: Config, pipeline: AIAgentFunctionalPipeline) : LoggingFeature {
            val logging = LoggingFeature(logger = logger)

            pipeline.interceptAgentStarting(this) { e ->
                logger.info { "Agent starting: runId=${e.runId}" }
            }
            pipeline.interceptStrategyStarting(this) { _ ->
                logger.info { "Strategy starting" }
            }
            pipeline.interceptLLMCallStarting(this) { e ->
                logger.info { "Making LLM call with ${e.tools.size} tools" }
            }
            pipeline.interceptLLMCallCompleted(this) { e ->
                logger.info { "Received ${e.responses.size} response(s)" }
            }
            return logging
        }
    }
}
```
<!--- KNIT example-custom-features-04.kt -->
