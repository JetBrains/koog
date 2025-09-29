package ai.koog.agents.features.tracing.feature

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.AIAgentGraphPipeline
import ai.koog.agents.core.feature.InterceptContext
import ai.koog.agents.core.feature.message.FeatureMessage
import ai.koog.agents.core.feature.message.FeatureMessageProcessorUtil.onMessageForEachCatching
import ai.koog.agents.core.feature.model.events.AgentClosingEvent
import ai.koog.agents.core.feature.model.events.AgentCompletedEvent
import ai.koog.agents.core.feature.model.events.AgentExecutionFailedEvent
import ai.koog.agents.core.feature.model.events.AgentStartingEvent
import ai.koog.agents.core.feature.model.events.GraphStrategyStartingEvent
import ai.koog.agents.core.feature.model.events.LLMCallCompletedEvent
import ai.koog.agents.core.feature.model.events.LLMCallStartingEvent
import ai.koog.agents.core.feature.model.events.NodeExecutionCompletedEvent
import ai.koog.agents.core.feature.model.events.NodeExecutionFailedEvent
import ai.koog.agents.core.feature.model.events.NodeExecutionStartingEvent
import ai.koog.agents.core.feature.model.events.StrategyCompletedEvent
import ai.koog.agents.core.feature.model.events.ToolCallCompletedEvent
import ai.koog.agents.core.feature.model.events.ToolCallFailedEvent
import ai.koog.agents.core.feature.model.events.ToolCallStartingEvent
import ai.koog.agents.core.feature.model.events.ToolValidationFailedEvent
import ai.koog.agents.core.feature.model.events.startNodeToGraph
import ai.koog.agents.core.feature.model.toAgentError
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.features.tracing.eventString
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Feature that collects comprehensive tracing data during agent execution and sends it to configured feature message processors.
 *
 * Tracing is crucial for evaluation and analysis of the working agent, as it captures detailed information about:
 * - All LLM calls and their responses
 * - Prompts sent to LLMs
 * - Tool calls, arguments, and results
 * - Graph node visits and execution flow
 * - Agent lifecycle events (creation, start, finish, errors)
 * - Strategy execution events
 *
 * This data can be used for debugging, performance analysis, auditing, and improving agent behavior.
 *
 * Example of installing tracing to an agent:
 * ```kotlin
 * val agent = AIAgent(
 *     promptExecutor = executor,
 *     strategy = strategy,
 *     // other parameters...
 * ) {
 *     install(Tracing) {
 *         // Configure message processors to handle trace events
 *         addMessageProcessor(TraceFeatureMessageLogWriter(logger))

 *         val fileWriter = TraceFeatureMessageFileWriter(
 *             outputFile,
 *             { path: Path -> SystemFileSystem.sink(path).buffered() }
 *         )
 *         addMessageProcessor(fileWriter)
 *
 *         // Optionally filter messages
 *         fileWriter.setMessageFilter { message ->
 *             // Only trace LLM calls and tool calls
 *             message is BeforeLLMCallEvent || message is ToolCallEvent
 *         }
 *     }
 * }
 * ```
 *
 * Example of logs produced by tracing:
 * ```
 * AIAgentStartedEvent (agentId: agent-123, runId: session-456, strategyName: my-agent-strategy)
 * AIAgentStrategyStartEvent (runId: session-456, strategyName: my-agent-strategy)
 * AIAgentNodeExecutionStartEvent (runId: session-456, nodeName: definePrompt, input: user query)
 * AIAgentNodeExecutionEndEvent (runId: session-456, nodeName: definePrompt, input: user query, output: processed query)
 * BeforeLLMCallEvent (runId: session-456, prompt: Please analyze the following code...)
 * AfterLLMCallEvent (runId: session-456, response: I've analyzed the code and found...)
 * ToolCallEvent (runId: session-456, toolName: readFile, toolArgs: {"path": "src/main.py"})
 * ToolCallResultEvent (runId: session-456, toolName: readFile, toolArgs: {"path": "src/main.py"}, result: "def main():...")
 * AIAgentStrategyFinishedEvent (runId: session-456, strategyName: my-agent-strategy, result: Success)
 * AIAgentFinishedEvent (agentId: agent-123, runId: session-456, result: Success)
 * ```
 */
public class Tracing {

    /**
     * Feature implementation for the Tracing functionality.
     *
     * This companion object implements [AIAgentFeature] and provides methods for creating
     * an initial configuration and installing the tracing feature in an agent pipeline.
     *
     * To use tracing in your agent, install it during agent creation:
     *
     * ```kotlin
     * val agent = AIAgent(...) {
     *     install(Tracing) {
     *         // Configure tracing here
     *         addMessageProcessor(TraceFeatureMessageLogWriter(logger))
     *     }
     * }
     * ```
     */
    public companion object Feature : AIAgentGraphFeature<TraceFeatureConfig, Tracing> {

        private val logger = KotlinLogging.logger { }

        override val key: AIAgentStorageKey<Tracing> =
            AIAgentStorageKey("agents-features-tracing")

        override fun createInitialConfig(): TraceFeatureConfig = TraceFeatureConfig()

        override fun install(
            config: TraceFeatureConfig,
            pipeline: AIAgentGraphPipeline,
        ) {
            logger.info { "Start installing feature: ${Tracing::class.simpleName}" }

            if (config.messageProcessors.isEmpty()) {
                logger.warn {
                    "Tracing Feature. No feature out stream providers are defined. Trace streaming has no target."
                }
            }

            val interceptContext = InterceptContext(this, Tracing())

            //region Intercept Agent Events

            pipeline.interceptAgentStarting(interceptContext) intercept@{ eventContext ->
                val event = AgentStartingEvent(
                    agentId = eventContext.agent.id,
                    runId = eventContext.runId,
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                processMessage(config, event)
            }

            pipeline.interceptAgentCompleted(interceptContext) intercept@{ eventContext ->
                val event = AgentCompletedEvent(
                    agentId = eventContext.agentId,
                    runId = eventContext.runId,
                    result = eventContext.result?.toString(),
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                processMessage(config, event)
            }

            pipeline.interceptAgentExecutionFailed(interceptContext) intercept@{ eventContext ->
                val event = AgentExecutionFailedEvent(
                    agentId = eventContext.agentId,
                    runId = eventContext.runId,
                    error = eventContext.throwable.toAgentError(),
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                processMessage(config, event)
            }

            pipeline.interceptAgentClosing(interceptContext) intercept@{ eventContext ->
                val event = AgentClosingEvent(
                    agentId = eventContext.agentId,
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                processMessage(config, event)
            }

            //endregion Intercept Agent Events

            //region Intercept Strategy Events

            pipeline.interceptStrategyStarting(interceptContext) intercept@{ eventContext ->
                val strategy = eventContext.strategy as AIAgentGraphStrategy

                @OptIn(InternalAgentsApi::class)
                val event = GraphStrategyStartingEvent(
                    runId = eventContext.runId,
                    strategyName = eventContext.strategy.name,
                    graph = strategy.startNodeToGraph(),
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                processMessage(config, event)
            }

            pipeline.interceptStrategyCompleted(interceptContext) intercept@{ eventContext ->
                val event = StrategyCompletedEvent(
                    runId = eventContext.runId,
                    strategyName = eventContext.strategy.name,
                    result = eventContext.result?.toString(),
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                processMessage(config, event)
            }

            //endregion Intercept Strategy Events

            //region Intercept Node Events

            pipeline.interceptNodeExecutionStarting(interceptContext) intercept@{ eventContext ->
                val event = NodeExecutionStartingEvent(
                    runId = eventContext.context.runId,
                    nodeName = eventContext.node.name,
                    input = eventContext.input?.toString() ?: "",
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                processMessage(config, event)
            }

            pipeline.interceptNodeExecutionCompleted(interceptContext) intercept@{ eventContext ->
                val event = NodeExecutionCompletedEvent(
                    runId = eventContext.context.runId,
                    nodeName = eventContext.node.name,
                    input = eventContext.input?.toString() ?: "",
                    output = eventContext.output?.toString() ?: "",
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                processMessage(config, event)
            }

            pipeline.interceptNodeExecutionFailed(interceptContext) intercept@{ eventContext ->
                val event = NodeExecutionFailedEvent(
                    runId = eventContext.context.runId,
                    nodeName = eventContext.node.name,
                    error = eventContext.throwable.toAgentError(),
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                processMessage(config, event)
            }

            //endregion Intercept Node Events

            //region Intercept LLM Call Events

            pipeline.interceptLLMCallStarting(interceptContext) intercept@{ eventContext ->
                val event = LLMCallStartingEvent(
                    runId = eventContext.runId,
                    prompt = eventContext.prompt,
                    model = eventContext.model.eventString,
                    tools = eventContext.tools.map { it.name },
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                processMessage(config, event)
            }

            pipeline.interceptLLMCallCompleted(interceptContext) intercept@{ eventContext ->
                val event = LLMCallCompletedEvent(
                    runId = eventContext.runId,
                    prompt = eventContext.prompt,
                    model = eventContext.model.eventString,
                    responses = eventContext.responses,
                    moderationResponse = eventContext.moderationResponse,
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                processMessage(config, event)
            }

            //endregion Intercept LLM Call Events

            //region Intercept Tool Call Events

            pipeline.interceptToolCallStarting(interceptContext) intercept@{ eventContext ->

                @Suppress("UNCHECKED_CAST")
                val tool = eventContext.tool as Tool<Any?, Any?>

                val event = ToolCallStartingEvent(
                    runId = eventContext.runId,
                    toolCallId = eventContext.toolCallId,
                    toolName = tool.name,
                    toolArgs = tool.encodeArgs(eventContext.toolArgs),
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                processMessage(config, event)
            }

            pipeline.interceptToolValidationFailed(interceptContext) intercept@{ eventContext ->

                @Suppress("UNCHECKED_CAST")
                val tool = eventContext.tool as Tool<Any?, Any?>

                val event = ToolValidationFailedEvent(
                    runId = eventContext.runId,
                    toolCallId = eventContext.toolCallId,
                    toolName = tool.name,
                    toolArgs = tool.encodeArgs(eventContext.toolArgs),
                    error = eventContext.error,
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                processMessage(config, event)
            }

            pipeline.interceptToolCallFailed(interceptContext) intercept@{ eventContext ->

                @Suppress("UNCHECKED_CAST")
                val tool = eventContext.tool as Tool<Any?, Any?>

                val event = ToolCallFailedEvent(
                    runId = eventContext.runId,
                    toolCallId = eventContext.toolCallId,
                    toolName = tool.name,
                    toolArgs = tool.encodeArgs(eventContext.toolArgs),
                    error = eventContext.throwable.toAgentError(),
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                processMessage(config, event)
            }

            pipeline.interceptToolCallCompleted(interceptContext) intercept@{ eventContext ->

                @Suppress("UNCHECKED_CAST")
                val tool = eventContext.tool as Tool<Any?, Any?>

                val event = ToolCallCompletedEvent(
                    runId = eventContext.runId,
                    toolCallId = eventContext.toolCallId,
                    toolName = tool.name,
                    toolArgs = tool.encodeArgs(eventContext.toolArgs),
                    result = eventContext.result?.let { result -> tool.encodeResultToString(result) },
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                processMessage(config, event)
            }

            //endregion Intercept Tool Call Events
        }

        //region Private Methods

        private suspend fun processMessage(config: TraceFeatureConfig, message: FeatureMessage) {
            config.messageProcessors.onMessageForEachCatching(message)
        }

        //endregion Private Methods
    }
}
