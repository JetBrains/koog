# Custom features

Features provide a way to extend and enhance the functionality of AI agents at runtime. They are designed to be modular
and composable, allowing you to mix and match them according to your needs.

In addition to [features](features-overview.md) that are available in Koog out of the box, you can also implement your 
own features by extending a proper feature interface. 
This page presents the basic building blocks for your own feature using the current Koog APIs.

## Feature interfaces

Koog provides two interfaces that you can extend to implement custom features:

- [AIAgentGraphFeature](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.feature/-a-i-agent-graph-feature/index.html): Represents a feature specific to [agents that have defined workflows](complex-workflow-agents.md) (graph-based agents).
- [AIAgentFunctionalFeature](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.feature/-a-i-agent-functional-feature/index.html): Represents a feature that can be used with [functional agents](functional-agents.md).

!!! note
    To create a custom feature that can be installed in both graph-based and functional agents, you need to extend both 
    interfaces.

## Implementing custom features

To implement a custom feature, you need to create a feature structure according to the following steps:

1. Create a feature class.
2. Define a configuration class. The configuration class is an extension of the [FeatureConfig](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.feature.config/-feature-config/index.html) class.
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
    promptExecutor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY")),
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

For a feature to receive a specific type of event, it needs to register the corresponding pipeline interceptor.

### Filtering agent events

When installing a feature in an agent, you may not want to handle all events that are registered in the feature. To
filter out some events, you apply filters using the [FeatureConfig.setEventFilter](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.feature.config/-feature-config/set-event-filter.html) function.

The following example shows how you can allow only LLM call start and end events for a feature:

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.feature.handler.AgentLifecycleEventType
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.agents.features.tracing.feature.Tracing

typealias MyFeature = Tracing

suspend fun main() {
    val agent = AIAgent(
        promptExecutor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY")),
        systemPrompt = "You are a helpful assistant. Answer user questions concisely.",
        llmModel = OpenAIModels.Chat.GPT4o
    ) {
        install(Tracing) {
-->
<!--- SUFFIX
        }
    }
}
-->
```kotlin
install(MyFeature) {
    setEventFilter { context ->
        context.eventType is AgentLifecycleEventType.LLMCallStarting ||
            context.eventType is AgentLifecycleEventType.LLMCallCompleted
    }
}
```
<!--- KNIT example-custom-features-03.kt -->

#### Disabling event filtering for a feature

Event filtering can cause unexpected behavior for some features that depend on the full event stream without filtering.
Examples of such features include [Debugger](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.feature.debugger/-debugger/index.html) and [OpenTelemetry](opentelemetry-support.md).

If your feature depends on the full event stream, disable event filtering when implementing the feature by overriding 
`setEventFilter` in your feature configuration to ignore any custom filters set when installing the feature:

<!--- INCLUDE
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.handler.AgentLifecycleEventContext
-->
```kotlin
class MyFeatureConfig : FeatureConfig() {
    override fun setEventFilter(filter: (AgentLifecycleEventContext) -> Boolean) {
        // Deactivate event filtering for the feature
        throw UnsupportedOperationException("Event filtering is not allowed.")
    }
}
```
<!--- KNIT example-custom-features-04.kt -->

## Example: A basic logging feature

The following example shows how to implement a basic logging feature that logs agent lifecycle events. As the feature
should be available in both graph-based and functional agents, interceptors that are common to both agent types are
implemented in the `installCommon` method to avoid code duplication. The interceptors that are specific to individual
agent types are implemented in the `installGraphPipeline` and `installFunctionalPipeline` methods.

<!--- INCLUDE
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.feature.AIAgentFunctionalFeature
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.pipeline.AIAgentFunctionalPipeline
import ai.koog.agents.core.feature.pipeline.AIAgentGraphPipeline
import ai.koog.agents.core.feature.pipeline.AIAgentPipeline
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
-->
```kotlin
class LoggingFeature(val loggerName: String) {
    class Config : FeatureConfig() {
        var loggerName: String = "agent-logs"
    }

    companion object Feature :
        AIAgentGraphFeature<Config, LoggingFeature>,
        AIAgentFunctionalFeature<Config, LoggingFeature> {

        override val key = createStorageKey<LoggingFeature>("logging-feature")

        override fun createInitialConfig(): Config = Config()

        override fun install(config: Config, pipeline: AIAgentGraphPipeline) : LoggingFeature {
            val logging = LoggingFeature(config.loggerName)
            val logger = KotlinLogging.logger(config.loggerName)

            installGraphPipeline(pipeline, logger)

            return logging
        }

        override fun install(config: Config, pipeline: AIAgentFunctionalPipeline) : LoggingFeature {
            val logging = LoggingFeature(config.loggerName)
            val logger = KotlinLogging.logger(config.loggerName)

            installFunctionalPipeline(pipeline, logger)

            return logging
        }

        private fun installCommon(
            pipeline: AIAgentPipeline,
            logger: KLogger,
        ) {
            pipeline.interceptAgentStarting(this) { e ->
                logger.info { "Agent starting: runId=${e.runId}" }
            }
            pipeline.interceptStrategyStarting(this) { e ->
                logger.info { "Strategy ${e.strategy.name} starting" }
            }
            pipeline.interceptLLMCallStarting(this) { e ->
                logger.info { "Making LLM call with ${e.tools.size} tools" }
            }
            pipeline.interceptLLMCallCompleted(this) { e ->
                logger.info { "Received ${e.responses.size} response(s)" }
            }
        }

        private fun installGraphPipeline(
            pipeline: AIAgentGraphPipeline,
            logger: KLogger,
        ) {
            installCommon(pipeline, logger)

            pipeline.interceptNodeExecutionStarting(this) { e ->
                logger.info { "Node ${e.node.name} input: ${e.input}" }
            }
            pipeline.interceptNodeExecutionCompleted(this) { e ->
                logger.info { "Node ${e.node.name} output: ${e.output}" }
            }
        }

        private fun installFunctionalPipeline(
            pipeline: AIAgentFunctionalPipeline,
            logger: KLogger
        ) {
            installCommon(pipeline, logger)
        }
    }
}
```
<!--- KNIT example-custom-features-05.kt -->

Here is an example of how to install the custom logging feature in an agent. The example shows a basic feature 
installation, along with the custom configuration property `loggerName` that lets you specify the name of the logger:

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.feature.AIAgentFunctionalFeature
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.pipeline.AIAgentFunctionalPipeline
import ai.koog.agents.core.feature.pipeline.AIAgentGraphPipeline
import ai.koog.agents.core.feature.pipeline.AIAgentPipeline
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor

class LoggingFeature(val loggerName: String) {
    class Config : FeatureConfig() {
        var loggerName: String = "agent-logs"
    }

    companion object Feature :
        AIAgentGraphFeature<Config, LoggingFeature>,
        AIAgentFunctionalFeature<Config, LoggingFeature> {

        override val key = createStorageKey<LoggingFeature>("logging-feature")

        override fun createInitialConfig(): Config = Config()

        override fun install(config: Config, pipeline: AIAgentGraphPipeline) : LoggingFeature {
            val logging = LoggingFeature(config.loggerName)
            val logger = KotlinLogging.logger(config.loggerName)

            installGraphPipeline(pipeline, logger)

            return logging
        }

        override fun install(config: Config, pipeline: AIAgentFunctionalPipeline) : LoggingFeature {
            val logging = LoggingFeature(config.loggerName)
            val logger = KotlinLogging.logger(config.loggerName)

            installFunctionalPipeline(pipeline, logger)

            return logging
        }

        private fun installCommon(
            pipeline: AIAgentPipeline,
            logger: KLogger,
        ) {
            pipeline.interceptAgentStarting(this) { e ->
                logger.info { "Agent starting: runId=${e.runId}" }
            }
            pipeline.interceptStrategyStarting(this) { e ->
                logger.info { "Strategy ${e.strategy.name} starting" }
            }
            pipeline.interceptLLMCallStarting(this) { e ->
                logger.info { "Making LLM call with ${e.tools.size} tools" }
            }
            pipeline.interceptLLMCallCompleted(this) { e ->
                logger.info { "Received ${e.responses.size} response(s)" }
            }
        }

        private fun installGraphPipeline(
            pipeline: AIAgentGraphPipeline,
            logger: KLogger,
        ) {
            installCommon(pipeline, logger)

            pipeline.interceptNodeExecutionStarting(this) { e ->
                logger.info { "Node ${e.node.name} input: ${e.input}" }
            }
            pipeline.interceptNodeExecutionCompleted(this) { e ->
                logger.info { "Node ${e.node.name} output: ${e.output}" }
            }
        }

        private fun installFunctionalPipeline(
            pipeline: AIAgentFunctionalPipeline,
            logger: KLogger
        ) {
            installCommon(pipeline, logger)
        }
    }
}

suspend fun main() {
-->
<!--- SUFFIX
}
-->
```kotlin
val agent = AIAgent(
    promptExecutor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY")),
    systemPrompt = "You are a helpful assistant. Answer user questions concisely.",
    llmModel = OpenAIModels.Chat.GPT4o
) {
    install(LoggingFeature) {
        loggerName = "my-custom-logger"
    }
}
agent.run("What is Kotlin?")
```
<!--- KNIT example-custom-features-06.kt -->
