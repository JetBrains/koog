package ai.koog.agents.features.tracing.feature

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.message.FeatureMessage
import ai.koog.agents.core.feature.message.FeatureMessageProcessorUtil.onMessageForEachCatching
import ai.koog.agents.core.feature.model.events.AgentClosingEvent
import ai.koog.agents.core.feature.model.events.AgentCompletedEvent
import ai.koog.agents.core.feature.model.events.AgentExecutionFailedEvent
import ai.koog.agents.core.feature.model.events.AgentStartingEvent
import ai.koog.agents.core.feature.model.events.GraphStrategyStartingEvent
import ai.koog.agents.core.feature.model.events.LLMCallCompletedEvent
import ai.koog.agents.core.feature.model.events.LLMCallStartingEvent
import ai.koog.agents.core.feature.model.events.LLMStreamingCompletedEvent
import ai.koog.agents.core.feature.model.events.LLMStreamingFailedEvent
import ai.koog.agents.core.feature.model.events.LLMStreamingFrameReceivedEvent
import ai.koog.agents.core.feature.model.events.LLMStreamingStartingEvent
import ai.koog.agents.core.feature.model.events.NodeExecutionCompletedEvent
import ai.koog.agents.core.feature.model.events.NodeExecutionFailedEvent
import ai.koog.agents.core.feature.model.events.NodeExecutionStartingEvent
import ai.koog.agents.core.feature.model.events.StrategyCompletedEvent
import ai.koog.agents.core.feature.model.events.SubgraphExecutionCompletedEvent
import ai.koog.agents.core.feature.model.events.SubgraphExecutionFailedEvent
import ai.koog.agents.core.feature.model.events.SubgraphExecutionStartingEvent
import ai.koog.agents.core.feature.model.events.ToolCallCompletedEvent
import ai.koog.agents.core.feature.model.events.ToolCallFailedEvent
import ai.koog.agents.core.feature.model.events.ToolCallStartingEvent
import ai.koog.agents.core.feature.model.events.ToolValidationFailedEvent
import ai.koog.agents.core.feature.model.events.startNodeToGraph
import ai.koog.agents.core.feature.model.toAgentError
import ai.koog.agents.core.feature.pipeline.AIAgentGraphPipeline
import ai.koog.agents.core.utils.SerializationUtils
import ai.koog.prompt.llm.toModelInfo
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.reflect.KType

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
 *             message is LLMCallStartingEvent || message is ToolCallEvent
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
 * LLMCallStartingEvent (runId: session-456, prompt: Please analyze the following code...)
 * LLMCallCompletedEvent (runId: session-456, response: I've analyzed the code and found...)
 * ToolCallEvent (runId: session-456, toolName: readFile, toolArgs: {"path": "src/main.py"})
 * ToolCallResultEvent (runId: session-456, toolName: readFile, toolArgs: {"path": "src/main.py"}, result: "def main():...")
 * AIAgentStrategyFinishedEvent (runId: session-456, strategyName: my-agent-strategy, result: Success)
 * AIAgentFinishedEvent (agentId: agent-123, runId: session-456, result: Success)
 * ```
 */
public class Tracing {

    /**
     * Companion object implementing agent feature, handling [Tracing] creation and installation.
     */
    public companion object Feature : AIAgentGraphFeature<TraceFeatureConfig, Tracing> {

        private val logger = KotlinLogging.logger { }

        override val key: AIAgentStorageKey<Tracing> =
            AIAgentStorageKey("agents-features-tracing")

        override fun createInitialConfig(): TraceFeatureConfig = TraceFeatureConfig()

        override fun install(
            config: TraceFeatureConfig,
            pipeline: AIAgentGraphPipeline,
        ): Tracing {
            logger.info { "Start installing feature: ${Tracing::class.simpleName}" }

            if (config.messageProcessors.isEmpty()) {
                logger.warn {
                    "Tracing Feature. No feature out stream providers are defined. Trace streaming has no target."
                }
            }

            val tracing = Tracing()

            //region Intercept Agent Events

            pipeline.interceptAgentStarting(this) intercept@{ eventContext ->
                val event = AgentStartingEvent(
                    agentId = eventContext.agent.id,
                    runId = eventContext.runId,
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                processMessage(config, event)
            }

            pipeline.interceptAgentCompleted(this) intercept@{ eventContext ->
                val event = AgentCompletedEvent(
                    agentId = eventContext.agentId,
                    runId = eventContext.runId,
                    result = eventContext.result?.toString(),
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                processMessage(config, event)
            }

            pipeline.interceptAgentExecutionFailed(this) intercept@{ eventContext ->
                val event = AgentExecutionFailedEvent(
                    agentId = eventContext.agentId,
                    runId = eventContext.runId,
                    error = eventContext.throwable.toAgentError(),
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                processMessage(config, event)
            }

            pipeline.interceptAgentClosing(this) intercept@{ eventContext ->
                val event = AgentClosingEvent(
                    agentId = eventContext.agentId,
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                processMessage(config, event)
            }

            //endregion Intercept Agent Events

            //region Intercept Strategy Events

            pipeline.interceptStrategyStarting(this) intercept@{ eventContext ->
                val strategy = eventContext.strategy as AIAgentGraphStrategy

                @OptIn(InternalAgentsApi::class)
                val event = GraphStrategyStartingEvent(
                    runId = eventContext.context.runId,
                    strategyName = eventContext.strategy.name,
                    graph = strategy.startNodeToGraph(),
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                processMessage(config, event)
            }

            pipeline.interceptStrategyCompleted(this) intercept@{ eventContext ->
                val event = StrategyCompletedEvent(
                    runId = eventContext.context.runId,
                    strategyName = eventContext.strategy.name,
                    result = eventContext.result?.toString(),
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                processMessage(config, event)
            }

            //endregion Intercept Strategy Events

            //region Intercept Node Events

            pipeline.interceptNodeExecutionStarting(this) intercept@{ eventContext ->
                val event = NodeExecutionStartingEvent(
                    runId = eventContext.context.runId,
                    nodeName = eventContext.node.name,
                    input = nodeDataToJsonElement(eventContext.input, eventContext.inputType),
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                processMessage(config, event)
            }

            pipeline.interceptNodeExecutionCompleted(this) intercept@{ eventContext ->
                val event = NodeExecutionCompletedEvent(
                    runId = eventContext.context.runId,
                    nodeName = eventContext.node.name,
                    input = nodeDataToJsonElement(eventContext.input, eventContext.inputType),
                    output = nodeDataToJsonElement(eventContext.output, eventContext.outputType),
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                processMessage(config, event)
            }

            pipeline.interceptNodeExecutionFailed(this) intercept@{ eventContext ->
                val event = NodeExecutionFailedEvent(
                    runId = eventContext.context.runId,
                    nodeName = eventContext.node.name,
                    input = nodeDataToJsonElement(eventContext.input, eventContext.inputType),
                    error = eventContext.throwable.toAgentError(),
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                processMessage(config, event)
            }

            //endregion Intercept Node Events

            //region Intercept Subgraph Events

            pipeline.interceptSubgraphExecutionStarting(this) intercept@{ eventContext ->
                val event = SubgraphExecutionStartingEvent(
                    runId = eventContext.context.runId,
                    subgraphName = eventContext.subgraph.name,
                    input = nodeDataToJsonElement(eventContext.input, eventContext.inputType),
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                processMessage(config, event)
            }

            pipeline.interceptSubgraphExecutionCompleted(this) intercept@{ eventContext ->
                val event = SubgraphExecutionCompletedEvent(
                    runId = eventContext.context.runId,
                    subgraphName = eventContext.subgraph.name,
                    input = nodeDataToJsonElement(eventContext.input, eventContext.inputType),
                    output = nodeDataToJsonElement(eventContext.output, eventContext.outputType),
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                processMessage(config, event)
            }

            pipeline.interceptSubgraphExecutionFailed(this) intercept@{ eventContext ->
                val event = SubgraphExecutionFailedEvent(
                    runId = eventContext.context.runId,
                    subgraphName = eventContext.subgraph.name,
                    input = nodeDataToJsonElement(eventContext.input, eventContext.inputType),
                    error = eventContext.throwable.toAgentError(),
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                processMessage(config, event)
            }

            //endregion Intercept Subgraph Events

            //region Intercept LLM Call Events

            pipeline.interceptLLMCallStarting(this) intercept@{ eventContext ->
                val event = LLMCallStartingEvent(
                    runId = eventContext.runId,
                    callId = eventContext.callId,
                    prompt = eventContext.prompt,
                    model = eventContext.model.toModelInfo(),
                    tools = eventContext.tools.map { it.name },
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                processMessage(config, event)
            }

            pipeline.interceptLLMCallCompleted(this) intercept@{ eventContext ->
                val event = LLMCallCompletedEvent(
                    runId = eventContext.runId,
                    callId = eventContext.callId,
                    prompt = eventContext.prompt,
                    model = eventContext.model.toModelInfo(),
                    responses = eventContext.responses,
                    moderationResponse = eventContext.moderationResponse,
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                processMessage(config, event)
            }

            //endregion Intercept LLM Call Events

            //region Intercept LLM Streaming Events

            pipeline.interceptLLMStreamingStarting(this) intercept@{ eventContext ->
                val event = LLMStreamingStartingEvent(
                    runId = eventContext.runId,
                    callId = eventContext.callId,
                    prompt = eventContext.prompt,
                    model = eventContext.model.toModelInfo(),
                    tools = eventContext.tools.map { it.name },
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                processMessage(config, event)
            }

            pipeline.interceptLLMStreamingCompleted(this) intercept@{ eventContext ->
                val event = LLMStreamingCompletedEvent(
                    runId = eventContext.runId,
                    callId = eventContext.callId,
                    prompt = eventContext.prompt,
                    model = eventContext.model.toModelInfo(),
                    tools = eventContext.tools.map { it.name },
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                processMessage(config, event)
            }

            pipeline.interceptLLMStreamingFrameReceived(this) intercept@{ eventContext ->
                val event = LLMStreamingFrameReceivedEvent(
                    runId = eventContext.runId,
                    callId = eventContext.callId,
                    frame = eventContext.streamFrame,
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                processMessage(config, event)
            }

            pipeline.interceptLLMStreamingFailed(this) intercept@{ eventContext ->
                val event = LLMStreamingFailedEvent(
                    runId = eventContext.runId,
                    callId = eventContext.callId,
                    error = eventContext.error.toAgentError(),
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                processMessage(config, event)
            }

            //endregion Intercept LLM Streaming Events

            //region Intercept Tool Call Events

            pipeline.interceptToolCallStarting(this) intercept@{ eventContext ->
                val event = ToolCallStartingEvent(
                    runId = eventContext.runId,
                    toolCallId = eventContext.toolCallId,
                    toolName = eventContext.tool.name,
                    toolArgs = eventContext.tool.encodeArgsUnsafe(eventContext.toolArgs),
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                processMessage(config, event)
            }

            pipeline.interceptToolValidationFailed(this) intercept@{ eventContext ->
                val event = ToolValidationFailedEvent(
                    runId = eventContext.runId,
                    toolCallId = eventContext.toolCallId,
                    toolName = eventContext.tool.name,
                    toolArgs = eventContext.tool.encodeArgsUnsafe(eventContext.toolArgs),
                    error = eventContext.error,
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                processMessage(config, event)
            }

            pipeline.interceptToolCallFailed(this) intercept@{ eventContext ->
                val event = ToolCallFailedEvent(
                    runId = eventContext.runId,
                    toolCallId = eventContext.toolCallId,
                    toolName = eventContext.tool.name,
                    toolArgs = eventContext.tool.encodeArgsUnsafe(eventContext.toolArgs),
                    error = eventContext.throwable.toAgentError(),
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                processMessage(config, event)
            }

            pipeline.interceptToolCallCompleted(this) intercept@{ eventContext ->
                val event = ToolCallCompletedEvent(
                    runId = eventContext.runId,
                    toolCallId = eventContext.toolCallId,
                    toolName = eventContext.tool.name,
                    toolArgs = eventContext.tool.encodeArgsUnsafe(eventContext.toolArgs),
                    result = eventContext.result?.let { result -> eventContext.tool.encodeResultToStringUnsafe(result) },
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                processMessage(config, event)
            }

            //endregion Intercept Tool Call Events

            return tracing
        }

        //region Private Methods

        private suspend fun processMessage(config: TraceFeatureConfig, message: FeatureMessage) {
            config.messageProcessors.onMessageForEachCatching(message)
        }

        /**
         * Retrieves the JSON representation of the given data based on its type.
         *
         * Note: See [KG-485](https://youtrack.jetbrains.com/issue/KG-485)
         *       Workaround for processing non-serializable [ReceivedToolResult] type in the node input/output.
         */
        private fun nodeDataToJsonElement(data: Any?, dataType: KType): JsonElement {
            @OptIn(InternalAgentsApi::class)
            return SerializationUtils.encodeDataToJsonElementOrDefault(data, dataType) {
                when (data) {
                    is ReceivedToolResult -> SerializationUtils.parseDataToJsonElementOrDefault(data.content)
                    else -> JsonPrimitive(data?.toString())
                }
            }
        }

        //endregion Private Methods
    }
}
