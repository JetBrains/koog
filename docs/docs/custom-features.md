# Custom features

Features provide a way to extend and enhance the functionality of AI agents at runtime. They are designed to be modular
and composable, allowing you to mix and match them according to your needs.

In addition to [features](features-overview.md) that are available in Koog out of the box, you can also implement your 
own features by extending a proper feature interface. 
This page presents the basic building blocks for your own feature using the current Koog APIs.

## Feature interfaces

Koog provides the following interfaces that you can extend to implement custom features:

- [AIAgentGraphFeature](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.feature/-a-i-agent-graph-feature/index.html): Represents a feature specific to [agents that have defined workflows](agents/graph-based-agents.md) (graph-based agents).
- [AIAgentFunctionalFeature](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.feature/-a-i-agent-functional-feature/index.html): Represents a feature that can be used with [functional agents](agents/functional-agents.md).
- [AIAgentPlannerFeature](https://api.koog.ai/agents/agents-planner/ai.koog.agents.planner/-a-i-agent-planner-feature/index.html): Represents a feature type that is specific to [planner agents](agents/planner-agents/index.md).

!!! note
    To create a custom feature that can be installed in graph-based, functional, and planner agents, you need to 
    implement all interfaces.

## Implementing custom features

To implement a custom feature, you need to create a feature structure according to the following steps:

1. Create a feature class.
2. Define a configuration class. The configuration class is an extension of the [FeatureConfig](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.feature.config/-feature-config/index.html) class.
3. Create a companion object that implements some or all of the following interfaces: [AIAgentGraphFeature](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.feature/-a-i-agent-graph-feature/index.html), [AIAgentFunctionalFeature](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.feature/-a-i-agent-functional-feature/index.html), [AIAgentPlannerFeature](https://api.koog.ai/agents/agents-planner/ai.koog.agents.planner/-a-i-agent-planner-feature/index.html).
4. Give your feature a unique storage key that is used for feature identification and retrieval in agent pipelines. The
   key is used inside the internal map in an agent pipeline that includes all registered features for an agent. When you run an agent, it needs to process all registered features, and the key is used to retrieve the feature from this map.
5. Implement the required methods.

The code sample below shows the general pattern for implementing a custom feature that can be installed in graph-based, 
functional, and planner agents:

<!--- INCLUDE
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.feature.AIAgentFunctionalFeature
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.AIAgentPlannerFeature
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.pipeline.AIAgentFunctionalPipeline
import ai.koog.agents.core.feature.pipeline.AIAgentGraphPipeline
import ai.koog.agents.core.feature.pipeline.AIAgentPlannerPipeline
-->
```kotlin
class MyFeature(val someProperty: String) {
    class Config : FeatureConfig() {
        var configProperty: String = "default"
    }

    companion object Feature : AIAgentGraphFeature<Config, MyFeature>, AIAgentFunctionalFeature<Config, MyFeature>, AIAgentPlannerFeature<Config, MyFeature> {
        // Unique storage key for retrieval in contexts
        override val key = createStorageKey<MyFeature>("my-feature")
        override fun createInitialConfig(agentConfig: AIAgentConfig): Config = Config()

        // Feature installation for graph-based agents
        override fun install(config: Config, pipeline: AIAgentGraphPipeline) : MyFeature {
            val feature = MyFeature(config.configProperty)

            pipeline.interceptAgentStarting(this) { context ->
                // Event handler implementation
            }
            return feature
        }

        // Feature installation for functional agents
        override fun install(config: Config, pipeline: AIAgentFunctionalPipeline) : MyFeature {
            val feature = MyFeature(config.configProperty)

            pipeline.interceptAgentStarting(this) { context ->
                // Event handler implementation
            }
            return feature
        }

        // Feature installation for planner agents
        override fun install(config: Config, pipeline: AIAgentPlannerPipeline) : MyFeature {
            val feature = MyFeature(config.configProperty)

            pipeline.interceptAgentStarting(this) { context ->
                // Event handler implementation
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
grouped by type and apply to graph-based, functional, and planner agent pipelines. To reduce noise and optimize cost 
when developing actual features, register only the interceptors you need for the feature.

Agent and environment lifecycle:

- `interceptEnvironmentCreated`: Transforms the agent environment when it’s created.
- `interceptAgentStarting`: Invoked before the start of an agent run.
- `interceptAgentCompleted`: Invoked when an agent run completes successfully.
- `interceptAgentExecutionFailed`: Invoked when an agent run fails.
- `interceptAgentClosing`: Invoked just before the agent run closes (cleanup point).

Strategy lifecycle: 

- `interceptStrategyStarting`: Invoked before the start of a strategy execution.
- `interceptStrategyCompleted`: Invoked when a strategy execution completes successfully.

LLM call lifecycle:

- `interceptLLMCallStarting`: Invoked before an LLM call.
- `interceptLLMCallCompleted`: Invoked after an LLM call.

LLM streaming lifecycle:

- `interceptLLMStreamingStarting`: Invoked before streaming starts.
- `interceptLLMStreamingFrameReceived`: Invoked for each received stream frame.
- `interceptLLMStreamingFailed`: Invoked when streaming fails.
- `interceptLLMStreamingCompleted`: Invoked after streaming completes.

Tool call lifecycle:

- `interceptToolCallStarting`: Invoked before a tool call.
- `interceptToolValidationFailed`: Invoked when tool input validation fails.
- `interceptToolCallFailed`: Invoked when tool execution fails.
- `interceptToolCallCompleted`: Invoked after the tool completes (with a result).

#### Interceptors specific to graph-based agents

The following interceptors are available only on `AIAgentGraphPipeline` and let you observe node and subgraph lifecycle events.

Node execution lifecycle:

- `interceptNodeExecutionStarting`: Invoked before a node starts executing.
- `interceptNodeExecutionCompleted`: Invoked after a node finishes executing.
- `interceptNodeExecutionFailed`: Invoked when a node execution fails with an error.

Subgraph execution lifecycle:

- `interceptSubgraphExecutionStarting`: Invoked right before a subgraph starts executing.
- `interceptSubgraphExecutionCompleted`: Invoked after a subgraph execution completes.
- `interceptSubgraphExecutionFailed`: Invoked when a subgraph execution fails.

For a feature to handle a specific type of event, it needs to register the corresponding pipeline interceptor.

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

If your feature logic relies on the complete agent event structure, event filtering can cause unexpected behavior. To
prevent this, you need to disable event filtering when implementing the feature by overriding `setEventFilter` in your 
feature configuration to ignore any custom filters set when installing the feature.

An example of a feature that relies on processing the entire agent event stream is [OpenTelemetry](opentelemetry-support.md), as it uses the 
complete agent event structure to compose an inherited structure of spans.

Here is an example of how to disable event filtering for a feature:

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
should be available graph-based, functional, and planner agents, interceptors that are common to all agent types are
implemented in the `installCommon` method to avoid code duplication. The interceptors that are specific to individual
agent types are implemented in the `installGraphPipeline`, `installFunctionalPipeline`, and `installPlannerPipeline`
methods.

<!--- INCLUDE
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.feature.AIAgentFunctionalFeature
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.AIAgentPlannerFeature
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.pipeline.AIAgentFunctionalPipeline
import ai.koog.agents.core.feature.pipeline.AIAgentGraphPipeline
import ai.koog.agents.core.feature.pipeline.AIAgentPlannerPipeline
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
        AIAgentFunctionalFeature<Config, LoggingFeature>,
        AIAgentPlannerFeature<Config, LoggingFeature> {

        override val key = createStorageKey<LoggingFeature>("logging-feature")

        override fun createInitialConfig(agentConfig: AIAgentConfig): Config = Config()

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

        override fun install(config: Config, pipeline: AIAgentPlannerPipeline) : LoggingFeature {
            val logging = LoggingFeature(config.loggerName)
            val logger = KotlinLogging.logger(config.loggerName)

            installPlannerPipeline(pipeline, logger)

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

        private fun installPlannerPipeline(
            pipeline: AIAgentPlannerPipeline,
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
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.feature.AIAgentFunctionalFeature
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.AIAgentPlannerFeature
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.pipeline.AIAgentFunctionalPipeline
import ai.koog.agents.core.feature.pipeline.AIAgentGraphPipeline
import ai.koog.agents.core.feature.pipeline.AIAgentPlannerPipeline
import ai.koog.agents.core.feature.pipeline.AIAgentPipeline
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging

class LoggingFeature(val loggerName: String) {
    class Config : FeatureConfig() {
        var loggerName: String = "agent-logs"
    }

    companion object Feature :
        AIAgentGraphFeature<Config, LoggingFeature>,
        AIAgentFunctionalFeature<Config, LoggingFeature>,
        AIAgentPlannerFeature<Config, LoggingFeature> {

        override val key = createStorageKey<LoggingFeature>("logging-feature")

        override fun createInitialConfig(agentConfig: AIAgentConfig): Config = Config()

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

        override fun install(config: Config, pipeline: AIAgentPlannerPipeline) : LoggingFeature {
            val logging = LoggingFeature(config.loggerName)
            val logger = KotlinLogging.logger(config.loggerName)

            installPlannerPipeline(pipeline, logger)

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

        private fun installPlannerPipeline(
            pipeline: AIAgentPlannerPipeline,
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
