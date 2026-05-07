package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.feature.AIAgentFunctionalFeature
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.AIAgentPlannerFeature
import ai.koog.agents.core.feature.handler.tool.ToolCallEventContext
import ai.koog.agents.core.feature.pipeline.AIAgentFunctionalPipeline
import ai.koog.agents.core.feature.pipeline.AIAgentGraphPipeline
import ai.koog.agents.core.feature.pipeline.AIAgentPipeline
import ai.koog.agents.core.feature.pipeline.AIAgentPlannerPipeline
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.opentelemetry.attribute.GenAIAttributes
import ai.koog.agents.features.opentelemetry.attribute.KoogAttributes
import ai.koog.agents.features.opentelemetry.extension.lastResponse
import ai.koog.agents.features.opentelemetry.extension.toFinishReason
import ai.koog.agents.features.opentelemetry.integration.SpanAdapter
import ai.koog.agents.features.opentelemetry.metric.MetricCollector
import ai.koog.agents.features.opentelemetry.metric.createMetricCollector
import ai.koog.agents.features.opentelemetry.metric.duration
import ai.koog.agents.features.opentelemetry.metric.events.createExecuteToolDurationHistogramMetricEvent
import ai.koog.agents.features.opentelemetry.metric.events.createLLMCallDurationHistogramMetricEvent
import ai.koog.agents.features.opentelemetry.metric.events.createLLMInputTokensMetricEvent
import ai.koog.agents.features.opentelemetry.metric.events.createLLMOutputTokensMetricEvent
import ai.koog.agents.features.opentelemetry.metric.events.createToolCallCounterMetricEvent
import ai.koog.agents.features.opentelemetry.metric.events.toLLMCallStartMetricEvent
import ai.koog.agents.features.opentelemetry.metric.events.toTimestampedMetricEvent
import ai.koog.agents.features.opentelemetry.span.GenAIAgentSpan
import ai.koog.agents.features.opentelemetry.span.SpanCollector
import ai.koog.agents.features.opentelemetry.span.SpanType
import ai.koog.agents.features.opentelemetry.span.endCreateAgentSpan
import ai.koog.agents.features.opentelemetry.span.endExecuteToolSpan
import ai.koog.agents.features.opentelemetry.span.endInferenceSpan
import ai.koog.agents.features.opentelemetry.span.endInvokeAgentSpan
import ai.koog.agents.features.opentelemetry.span.endNodeExecuteSpan
import ai.koog.agents.features.opentelemetry.span.endStrategySpan
import ai.koog.agents.features.opentelemetry.span.endSubgraphExecuteSpan
import ai.koog.agents.features.opentelemetry.span.startCreateAgentSpan
import ai.koog.agents.features.opentelemetry.span.startExecuteToolSpan
import ai.koog.agents.features.opentelemetry.span.startInferenceSpan
import ai.koog.agents.features.opentelemetry.span.startInvokeAgentSpan
import ai.koog.agents.features.opentelemetry.span.startNodeExecuteSpan
import ai.koog.agents.features.opentelemetry.span.startStrategySpan
import ai.koog.agents.features.opentelemetry.span.startSubgraphExecuteSpan
import ai.koog.agents.mcp.metadata.McpMetadataKeys
import ai.koog.serialization.JSONElement
import ai.koog.serialization.JSONObject
import ai.koog.serialization.JSONSerializer
import ai.koog.serialization.TypeToken
import ai.koog.serialization.kotlinx.toKotlinxJsonElement
import ai.koog.serialization.kotlinx.toKotlinxJsonObject
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Represents the OpenTelemetry integration feature for tracking and managing spans and contexts
 * within the AI Agent framework. This class manages the lifecycle of spans for various operations,
 * including agent executions, node processing, LLM calls, and tool calls.
 */
public class OpenTelemetry {

    /**
     * Companion object implementing agent feature, handling [OpenTelemetry] creation and installation.
     */
    public companion object Feature :
        AIAgentGraphFeature<OpenTelemetryConfig, OpenTelemetry>,
        AIAgentFunctionalFeature<OpenTelemetryConfig, OpenTelemetry>,
        AIAgentPlannerFeature<OpenTelemetryConfig, OpenTelemetry> {

        private val logger = KotlinLogging.logger { }

        override val key: AIAgentStorageKey<OpenTelemetry> = AIAgentStorageKey("agents-features-opentelemetry")

        override fun createInitialConfig(
            agentConfig: AIAgentConfig
        ): OpenTelemetryConfig {
            return OpenTelemetryConfig()
        }

        override fun install(
            config: OpenTelemetryConfig,
            pipeline: AIAgentGraphPipeline
        ): OpenTelemetry {
            val openTelemetry = OpenTelemetry()
            val spanCollector = SpanCollector()
            val spanAdapter = config.spanAdapter
            val tracer = config.tracer
            val contextFactory = config.contextFactory

            installCommon(config, pipeline, spanCollector)

            //region Node

            pipeline.interceptNodeExecutionStarting(this) intercept@{ eventContext ->
                logger.debug { "Execute OpenTelemetry node starting handler" }

                val patchedExecutionInfo = eventContext.executionInfo.appendRunId(eventContext.context.runId)
                val parentSpan = spanCollector.getParentSpanForEvent(
                    executionInfo = patchedExecutionInfo,
                ) ?: run {
                    logger.warn { "Failed to find parent span for node: ${eventContext.node.id}. Path: ${patchedExecutionInfo.path()}" }
                    return@intercept
                }

                val nodeInput = nodeDataToString(eventContext.input, eventContext.inputType, pipeline.config.serializer)

                val nodeExecuteSpan = startNodeExecuteSpan(
                    tracer = tracer,
                    contextFactory = contextFactory,
                    parentSpan = parentSpan,
                    id = eventContext.eventId,
                    runId = eventContext.context.runId,
                    nodeId = eventContext.node.id,
                    nodeInput = nodeInput,
                    spanAdapter = spanAdapter,
                )

                spanCollector.collectSpan(
                    span = nodeExecuteSpan,
                    path = patchedExecutionInfo
                )
            }

            pipeline.interceptNodeExecutionCompleted(this) intercept@{ eventContext ->
                logger.debug { "Execute OpenTelemetry node completed handler" }

                // Find existing span (Node Execute Span)
                val patchedExecutionInfo = eventContext.executionInfo.appendRunId(eventContext.context.runId)
                val nodeExecuteSpan = spanCollector.getStartedSpan(
                    executionInfo = patchedExecutionInfo,
                    eventId = eventContext.eventId,
                    spanType = SpanType.NODE
                ) ?: return@intercept

                val nodeOutput = nodeDataToString(eventContext.output, eventContext.outputType, pipeline.config.serializer)

                endNodeExecuteSpan(
                    span = nodeExecuteSpan,
                    nodeOutput = nodeOutput,
                    verbose = config.isVerbose,
                    spanAdapter = spanAdapter,
                )
                spanCollector.removeSpan(
                    span = nodeExecuteSpan,
                    path = patchedExecutionInfo
                )
            }

            pipeline.interceptNodeExecutionFailed(this) intercept@{ eventContext ->
                logger.debug { "Execute OpenTelemetry node execution failed handler" }

                // Find existing span (Node Execute Span)
                val patchedExecutionInfo = eventContext.executionInfo.appendRunId(eventContext.context.runId)
                val nodeExecuteSpan = spanCollector.getStartedSpan(
                    executionInfo = patchedExecutionInfo,
                    eventId = eventContext.eventId,
                    spanType = SpanType.NODE
                ) ?: return@intercept

                endNodeExecuteSpan(
                    span = nodeExecuteSpan,
                    nodeOutput = null,
                    error = eventContext.error,
                    verbose = config.isVerbose,
                    spanAdapter = spanAdapter,
                )
                spanCollector.removeSpan(
                    span = nodeExecuteSpan,
                    path = patchedExecutionInfo
                )
            }

            //endregion Node

            //region Subgraph

            pipeline.interceptSubgraphExecutionStarting(this) intercept@{ eventContext ->
                logger.debug { "Execute OpenTelemetry before subgraph handler" }

                val patchedExecutionInfo = eventContext.executionInfo.appendRunId(eventContext.context.runId)
                val parentSpan = spanCollector.getParentSpanForEvent(
                    executionInfo = patchedExecutionInfo,
                ) ?: return@intercept

                val subgraphInput = nodeDataToString(eventContext.input, eventContext.inputType, pipeline.config.serializer)

                val subgraphExecuteSpan = startSubgraphExecuteSpan(
                    tracer = tracer,
                    contextFactory = contextFactory,
                    parentSpan = parentSpan,
                    id = eventContext.eventId,
                    runId = eventContext.context.runId,
                    subgraphId = eventContext.subgraph.id,
                    subgraphInput = subgraphInput,
                    spanAdapter = spanAdapter,
                )

                spanCollector.collectSpan(
                    span = subgraphExecuteSpan,
                    path = patchedExecutionInfo
                )
            }

            pipeline.interceptSubgraphExecutionCompleted(this) intercept@{ eventContext ->
                logger.debug { "Execute OpenTelemetry after subgraph handler" }

                // Find the existing span (Subgraph Execute Span)
                val patchedExecutionInfo = eventContext.executionInfo.appendRunId(eventContext.context.runId)
                val subgraphExecuteSpan = spanCollector.getStartedSpan(
                    executionInfo = patchedExecutionInfo,
                    eventId = eventContext.eventId,
                    spanType = SpanType.SUBGRAPH
                ) ?: return@intercept

                val subgraphOutput = nodeDataToString(eventContext.output, eventContext.outputType, pipeline.config.serializer)

                endSubgraphExecuteSpan(
                    span = subgraphExecuteSpan,
                    subgraphOutput = subgraphOutput,
                    verbose = config.isVerbose,
                    spanAdapter = spanAdapter,
                )
                spanCollector.removeSpan(
                    span = subgraphExecuteSpan,
                    path = patchedExecutionInfo
                )
            }

            pipeline.interceptSubgraphExecutionFailed(this) intercept@{ eventContext ->
                logger.debug { "Execute OpenTelemetry subgraph execution error handler" }

                // Find the existing span (Subgraph Execute Span)
                val patchedExecutionInfo = eventContext.executionInfo.appendRunId(eventContext.context.runId)
                val subgraphExecuteSpan = spanCollector.getStartedSpan(
                    executionInfo = patchedExecutionInfo,
                    eventId = eventContext.eventId,
                    spanType = SpanType.SUBGRAPH
                ) ?: return@intercept

                endSubgraphExecuteSpan(
                    span = subgraphExecuteSpan,
                    subgraphOutput = null,
                    error = eventContext.error,
                    verbose = config.isVerbose,
                    spanAdapter = spanAdapter,
                )
                spanCollector.removeSpan(
                    span = subgraphExecuteSpan,
                    path = patchedExecutionInfo
                )
            }

            //endregion Subgraph

            return openTelemetry
        }

        override fun install(
            config: OpenTelemetryConfig,
            pipeline: AIAgentFunctionalPipeline
        ): OpenTelemetry {
            val openTelemetry = OpenTelemetry()
            val spanCollector = SpanCollector()

            installCommon(config, pipeline, spanCollector)

            return openTelemetry
        }

        override fun install(
            config: OpenTelemetryConfig,
            pipeline: AIAgentPlannerPipeline
        ): OpenTelemetry {
            val openTelemetry = OpenTelemetry()
            val spanCollector = SpanCollector()

            installCommon(config, pipeline, spanCollector)

            return openTelemetry
        }

        //region Private Methods

        private fun installCommon(
            config: OpenTelemetryConfig,
            pipeline: AIAgentPipeline,
            spanCollector: SpanCollector,
        ) {
            val spanAdapter = config.spanAdapter
            val tracer = config.tracer
            val contextFactory = config.contextFactory

            val metricCollector: MetricCollector = createMetricCollector(config)

            //region Agent

            pipeline.interceptAgentStarting(this) intercept@{ eventContext ->
                logger.debug { "Execute OpenTelemetry before agent started handler" }

                val messages = eventContext.agent.agentConfig.prompt.messages.toList()
                val tools = eventContext.context.llm.toolRegistry.tools.map { it.descriptor }.toList()

                // Create CreateAgentSpan
                val createAgentSpan = startCreateAgentSpan(
                    tracer = tracer,
                    contextFactory = contextFactory,
                    parentSpan = null,
                    id = eventContext.eventId,
                    model = eventContext.agent.agentConfig.model,
                    agentId = eventContext.context.agentId,
                    messages = messages,
                    spanAdapter = spanAdapter,
                )

                spanCollector.collectSpan(
                    span = createAgentSpan,
                    path = eventContext.executionInfo
                )

                // Create InvokeAgentSpan
                val invokeAgentSpan = startInvokeAgentSpan(
                    tracer = tracer,
                    contextFactory = contextFactory,
                    parentSpan = createAgentSpan,
                    id = eventContext.runId,
                    model = eventContext.agent.agentConfig.model,
                    agentId = eventContext.agent.id,
                    runId = eventContext.runId,
                    llmParams = eventContext.agent.agentConfig.prompt.params,
                    messages = messages,
                    tools = tools,
                    spanAdapter = spanAdapter,
                )

                // Patch the agent execution info to include runId in the path.
                // This is required to create a path structure that matches the span structure in the OTel feature.
                spanCollector.collectSpan(
                    span = invokeAgentSpan,
                    path = eventContext.executionInfo.appendRunId(eventContext.runId)
                )
            }

            pipeline.interceptAgentCompleted(this) intercept@{ eventContext ->
                logger.debug { "Execute OpenTelemetry agent finished handler" }

                // Find parent span - InvokeAgentSpan
                val patchedExecutionInfo = eventContext.executionInfo.appendRunId(eventContext.runId)
                val invokeAgentSpan = spanCollector.getStartedSpan(
                    executionInfo = patchedExecutionInfo,
                    eventId = eventContext.runId,
                    spanType = SpanType.INVOKE_AGENT
                ) ?: return@intercept

                eventContext.context.llm.prompt.messages.lastResponse()?.let { response ->
                    invokeAgentSpan.addAttribute(
                        GenAIAttributes.Response.FinishReasons(reasons = listOf(response.toFinishReason()))
                    )
                }

                endInvokeAgentSpan(
                    span = invokeAgentSpan,
                    messages = eventContext.context.config.prompt.messages.toList(),
                    model = eventContext.context.config.model,
                    verbose = config.isVerbose,
                    spanAdapter = spanAdapter,
                )
                spanCollector.removeSpan(
                    span = invokeAgentSpan,
                    path = patchedExecutionInfo
                )
            }

            pipeline.interceptAgentExecutionFailed(this) intercept@{ eventContext ->
                logger.debug { "Execute OpenTelemetry agent run error handler" }

                // Record any pending operation-duration metric events (e.g., an LLM call that
                // started but never completed) as failed measurements per GenAI semconv.
                metricCollector.flushPendingAsErrors(eventContext.error)

                // Stop all unfinished spans, except InvokeAgentSpan and AgentCreateSpan
                endUnfinishedSpans(
                    spanCollector = spanCollector,
                    spanAdapter = spanAdapter,
                    verbose = config.isVerbose,
                ) { span ->
                    span.type != SpanType.CREATE_AGENT &&
                        span.type != SpanType.INVOKE_AGENT &&
                        span.id != eventContext.eventId
                }

                // Finish current InvokeAgentSpan
                val patchedExecutionInfo = eventContext.executionInfo.appendRunId(eventContext.runId)
                val invokeAgentSpan = spanCollector.getStartedSpan(
                    executionInfo = patchedExecutionInfo,
                    eventId = eventContext.runId,
                    spanType = SpanType.INVOKE_AGENT
                ) ?: return@intercept

                endInvokeAgentSpan(
                    span = invokeAgentSpan,
                    messages = eventContext.context.config.prompt.messages.toList(),
                    model = eventContext.context.config.model,
                    error = eventContext.error,
                    verbose = config.isVerbose,
                    spanAdapter = spanAdapter,
                )
                spanCollector.removeSpan(
                    span = invokeAgentSpan,
                    path = patchedExecutionInfo
                )
            }

            pipeline.interceptAgentClosing(this) intercept@{ eventContext ->
                logger.debug { "Execute OpenTelemetry before agent closed handler" }

                // Flush any still-pending operation-duration start events as failures so we
                // don't leak them and so their durations are still reported per semconv.
                metricCollector.flushPendingAsErrors(error = null)

                // Stop all unfinished spans, except the AgentCreateSpan
                endUnfinishedSpans(
                    spanCollector = spanCollector,
                    verbose = config.isVerbose,
                    spanAdapter = spanAdapter,
                ) { span ->
                    span.type != SpanType.CREATE_AGENT
                }

                // Stop agent create span
                val agentSpan = spanCollector.getStartedSpan(
                    executionInfo = eventContext.executionInfo,
                    eventId = eventContext.eventId,
                    spanType = SpanType.CREATE_AGENT
                ) ?: return@intercept

                endCreateAgentSpan(
                    span = agentSpan,
                    verbose = config.isVerbose,
                    spanAdapter = spanAdapter,
                )

                spanCollector.removeSpan(
                    span = agentSpan,
                    path = eventContext.executionInfo
                )

                // Just in case we miss some spans, stop them as well
                if (spanCollector.activeSpansCount > 0) {
                    logger.warn { "Found <${spanCollector.activeSpansCount}> active span(s) after agent closing. Stopping them." }
                    endUnfinishedSpans(
                        spanCollector = spanCollector,
                        verbose = config.isVerbose,
                        spanAdapter = spanAdapter,
                    )
                }

                if (config.isShutdownOnAgentClose) {
                    config.closeSdks()
                }
            }

            //endregion Agent

            //region Strategy

            pipeline.interceptStrategyStarting(this) intercept@{ eventContext ->
                logger.debug { "Execute OpenTelemetry before subgraph handler" }

                val patchedExecutionInfo = eventContext.executionInfo.appendRunId(eventContext.context.runId)
                val parentSpan = spanCollector.getParentSpanForEvent(
                    executionInfo = patchedExecutionInfo,
                ) ?: return@intercept

                val strategySpan = startStrategySpan(
                    tracer = tracer,
                    contextFactory = contextFactory,
                    parentSpan = parentSpan,
                    id = eventContext.eventId,
                    runId = eventContext.context.runId,
                    strategyName = eventContext.strategy.name,
                    spanAdapter = spanAdapter,
                )

                spanCollector.collectSpan(
                    span = strategySpan,
                    path = patchedExecutionInfo
                )
            }

            pipeline.interceptStrategyCompleted(this) intercept@{ eventContext ->
                // Find current Strategy Span
                val patchedExecutionInfo = eventContext.executionInfo.appendRunId(eventContext.context.runId)
                val strategySpan = spanCollector.getStartedSpan(
                    executionInfo = patchedExecutionInfo,
                    eventId = eventContext.eventId,
                    spanType = SpanType.STRATEGY
                ) ?: return@intercept

                endStrategySpan(
                    span = strategySpan,
                    verbose = config.isVerbose,
                    spanAdapter = spanAdapter,
                )
                spanCollector.removeSpan(
                    span = strategySpan,
                    path = patchedExecutionInfo
                )
            }

            //endregion Strategy

            //region LLM Call

            pipeline.interceptLLMCallStarting(this) intercept@{ eventContext ->
                logger.debug { "Execute OpenTelemetry before LLM call handler" }

                val patchedExecutionInfo = eventContext.executionInfo
                    .appendRunId(eventContext.runId)
                    .appendId(eventContext.eventId)

                val parentSpan = spanCollector.getParentSpanForEvent(
                    executionInfo = patchedExecutionInfo,
                ) ?: return@intercept

                val messages = eventContext.prompt.messages.toList()

                val inferenceSpan = startInferenceSpan(
                    tracer = tracer,
                    contextFactory = contextFactory,
                    parentSpan = parentSpan,
                    id = eventContext.eventId,
                    provider = eventContext.model.provider,
                    runId = eventContext.runId,
                    model = eventContext.model,
                    messages = messages,
                    llmParams = eventContext.prompt.params,
                    tools = eventContext.tools,
                    spanAdapter = spanAdapter,
                )

                spanCollector.collectSpan(
                    span = inferenceSpan,
                    path = patchedExecutionInfo
                )

                // Metrics
                metricCollector.storeMetricEvent(eventContext.toLLMCallStartMetricEvent())
            }

            pipeline.interceptLLMCallCompleted(this) intercept@{ eventContext ->
                logger.debug { "Execute OpenTelemetry after LLM call handler" }

                // Find the current span (Inference Span)
                val patchedExecutionInfo = eventContext.executionInfo
                    .appendRunId(eventContext.runId)
                    .appendId(eventContext.eventId)

                val inferenceSpan = spanCollector.getStartedSpan(
                    executionInfo = patchedExecutionInfo,
                    eventId = eventContext.eventId,
                    spanType = SpanType.INFERENCE
                ) ?: return@intercept

                // Moderation outcome — not part of OTel GenAI semconv, captured as a Koog
                // namespaced attribute on the inference span when present.
                eventContext.moderationResponse?.let { response ->
                    inferenceSpan.addAttribute(KoogAttributes.Koog.Moderation.Result(response))
                }

                // Finish Reasons Attribute
                eventContext.responses.lastOrNull()?.let { response ->
                    inferenceSpan.addAttribute(
                        GenAIAttributes.Response.FinishReasons(reasons = listOf(response.toFinishReason()))
                    )
                }

                // Stop InferenceSpan
                endInferenceSpan(
                    span = inferenceSpan,
                    messages = eventContext.responses,
                    model = eventContext.model,
                    verbose = config.isVerbose,
                    spanAdapter = spanAdapter,
                )
                spanCollector.removeSpan(
                    span = inferenceSpan,
                    path = patchedExecutionInfo
                )

                eventContext.responses.lastOrNull()?.metaInfo?.inputTokensCount?.toLong()?.let { inputTokens ->
                    metricCollector.recordHistogramMetricEvent(
                        metricEvent = createLLMInputTokensMetricEvent(
                            id = eventContext.eventId,
                            model = eventContext.model,
                            inputTokens = inputTokens,
                        )
                    )
                }

                eventContext.responses.lastOrNull()?.metaInfo?.outputTokensCount?.toLong()?.let { outputTokens ->
                    metricCollector.recordHistogramMetricEvent(
                        metricEvent = createLLMOutputTokensMetricEvent(
                            id = eventContext.eventId,
                            model = eventContext.model,
                            outputTokens = outputTokens,
                        )
                    )
                }

                // Metrics
                metricCollector.getMetricEvent(eventContext.eventId)?.let { storedMetricEvent ->
                    metricCollector.recordHistogramMetricEvent(
                        metricEvent = createLLMCallDurationHistogramMetricEvent(
                            id = eventContext.eventId,
                            model = eventContext.model,
                            duration = storedMetricEvent.duration()
                        )
                    )
                }
            }

            pipeline.interceptLLMCallFailed(this) intercept@{ eventContext ->
                logger.debug { "Execute OpenTelemetry LLM call failure handler" }

                // Find the current span (Inference Span)
                val patchedExecutionInfo = eventContext.executionInfo
                    .appendRunId(eventContext.runId)
                    .appendId(eventContext.eventId)

                val inferenceSpan = spanCollector.getStartedSpan(
                    executionInfo = patchedExecutionInfo,
                    eventId = eventContext.eventId,
                    spanType = SpanType.INFERENCE
                ) ?: return@intercept

                endInferenceSpan(
                    span = inferenceSpan,
                    messages = emptyList(),
                    model = eventContext.model,
                    error = eventContext.error,
                    verbose = config.isVerbose,
                    spanAdapter = spanAdapter,
                )
                spanCollector.removeSpan(
                    span = inferenceSpan,
                    path = patchedExecutionInfo
                )
            }

            //endregion LLM Call

            //region Tool Call

            pipeline.interceptToolCallStarting(this) intercept@{ eventContext ->
                logger.debug { "Execute OpenTelemetry tool call handler" }

                val path = eventContext.executionInfo.appendRunId(eventContext.runId).appendId(eventContext.eventId)
                val parentSpan = spanCollector.getParentSpanForEvent(
                    executionInfo = path,
                ) ?: return@intercept

                val executeToolSpan = startExecuteToolSpan(
                    tracer = tracer,
                    contextFactory = contextFactory,
                    parentSpan = parentSpan,
                    id = eventContext.eventId,
                    toolName = eventContext.toolName,
                    toolArgs = eventContext.toolArgs.toKotlinxJsonObject(),
                    toolDescription = eventContext.toolDescription,
                    toolCallId = eventContext.toolCallId,
                    mcpToolMetadata = eventContext.context.llm.toolRegistry.getMcpToolMeta(eventContext.toolName),
                    spanAdapter = spanAdapter,
                )

                spanCollector.collectSpan(executeToolSpan, path)

                // Metrics
                metricCollector.storeMetricEvent(eventContext.toTimestampedMetricEvent())
            }

            pipeline.interceptToolCallCompleted(this) intercept@{ eventContext ->
                logger.debug { "Execute OpenTelemetry tool result handler" }
                val toolResult = eventContext.toolResult ?: JSONObject(emptyMap())
                endAndRemoveExecuteToolSpan(
                    toolResult = toolResult,
                    config = config,
                    spanAdapter = spanAdapter,
                    spanCollector = spanCollector,
                    eventContext = eventContext,
                )

                // Metrics
                metricCollector.addCounterMetricEvent(
                    metricEvent = createToolCallCounterMetricEvent(
                        id = eventContext.eventId,
                        toolName = eventContext.toolName,
                        toolCallStatus = KoogAttributes.Koog.Tool.Call.StatusType.SUCCESS
                    )
                )

                metricCollector.getMetricEvent(eventContext.eventId)?.let { storedMetricEvent ->
                    metricCollector.recordHistogramMetricEvent(
                        metricEvent = createExecuteToolDurationHistogramMetricEvent(
                            id = eventContext.eventId,
                            duration = storedMetricEvent.duration(),
                            toolName = eventContext.toolName,
                            toolCallStatus = KoogAttributes.Koog.Tool.Call.StatusType.SUCCESS
                        )
                    )
                }
            }

            pipeline.interceptToolCallFailed(this) intercept@{ eventContext ->
                logger.debug { "Execute OpenTelemetry tool call failure handler" }
                endAndRemoveExecuteToolSpan(
                    spanAdapter = spanAdapter,
                    spanCollector = spanCollector,
                    toolResult = JSONObject(emptyMap()),
                    config = config,
                    eventContext = eventContext,
                    error = eventContext.error,
                )

                // Metrics
                metricCollector.addCounterMetricEvent(
                    metricEvent = createToolCallCounterMetricEvent(
                        id = eventContext.eventId,
                        toolName = eventContext.toolName,
                        toolCallStatus = KoogAttributes.Koog.Tool.Call.StatusType.ERROR
                    )
                )

                metricCollector.getMetricEvent(eventContext.eventId)?.let { storedMetricEvent ->
                    metricCollector.recordHistogramMetricEvent(
                        metricEvent = createExecuteToolDurationHistogramMetricEvent(
                            id = eventContext.eventId,
                            duration = storedMetricEvent.duration(),
                            toolName = eventContext.toolName,
                            toolCallStatus = KoogAttributes.Koog.Tool.Call.StatusType.ERROR,
                            error = eventContext.error,
                        )
                    )
                }
            }

            pipeline.interceptToolValidationFailed(this) intercept@{ eventContext ->
                logger.debug { "Execute OpenTelemetry tool validation error handler" }
                endAndRemoveExecuteToolSpan(
                    spanAdapter = spanAdapter,
                    spanCollector = spanCollector,
                    toolResult = null,
                    config = config,
                    eventContext = eventContext,
                    error = eventContext.error,
                )

                // Metrics
                metricCollector.addCounterMetricEvent(
                    metricEvent = createToolCallCounterMetricEvent(
                        id = eventContext.eventId,
                        toolName = eventContext.toolName,
                        toolCallStatus = KoogAttributes.Koog.Tool.Call.StatusType.VALIDATION_FAILED
                    )
                )

                metricCollector.getMetricEvent(eventContext.eventId)?.let { storedMetricEvent ->
                    metricCollector.recordHistogramMetricEvent(
                        metricEvent = createExecuteToolDurationHistogramMetricEvent(
                            id = eventContext.eventId,
                            duration = storedMetricEvent.duration(),
                            toolName = eventContext.toolName,
                            toolCallStatus = KoogAttributes.Koog.Tool.Call.StatusType.VALIDATION_FAILED,
                            error = eventContext.error,
                        )
                    )
                }
            }

            //endregion Tool Call
        }

        /**
         * Retrieves the string JSON representation of the given data based on its type, skips it if it's not serializable,
         * returning null.
         */
        private fun nodeDataToString(data: Any?, dataType: TypeToken, serializer: JSONSerializer): String? {
            data ?: return null

            return try {
                serializer.encodeToString(data, dataType)
            } catch (_: Exception) {
                null
            }
        }

        /**
         * Retrieves the parent span for a given event context if available.
         * Checks if the event ID matches any child spans of the parent span node.
         *
         * @param executionInfo The execution information of the current event context.
         * @return The parent span associated with the event if found, or null if no matching parent is found.
         */
        private suspend fun SpanCollector.getParentSpanForEvent(
            executionInfo: AgentExecutionInfo
        ): GenAIAgentSpan? {
            var parentPath: AgentExecutionInfo? = executionInfo.parent ?: return null
            var parentSpan: GenAIAgentSpan? = null

            while (parentSpan == null && parentPath != null) {
                parentSpan = this.getSpan(path = parentPath)
                parentPath = parentPath.parent
            }

            return parentSpan
        }

        /**
         * Patches the execution information of the current event context by injecting the given run ID
         * into the hierarchy of `AgentExecutionInfo` instances. This ensures that data gathered from the
         * agent execution into matches spans composition: Agent | Run | Strategy | ...
         *
         * @param runId The run identifier to be injected into the execution information hierarchy.
         *              Can be null for creating top level agent spans,
         *              e.g., span with type [ai.koog.agents.features.opentelemetry.span.SpanType.CREATE_AGENT].
         * @return The updated [AgentExecutionInfo] object, reflecting the injected run ID where applicable.
         */
        internal fun appendRunId(executionInfo: AgentExecutionInfo, runId: String?): AgentExecutionInfo {
            runId ?: return executionInfo

            fun update(info: AgentExecutionInfo): AgentExecutionInfo {
                // Top level path
                if (info.parent == null) {
                    // Inject the agent run id path to match the OTel span structure and handle the Invoke Agent Span.
                    return AgentExecutionInfo(info, runId)
                }

                // Update the parent and reconstruct the node.
                val updatedParent = update(info.parent!!)
                return AgentExecutionInfo(updatedParent, info.partName)
            }

            return update(executionInfo)
        }

        /**
         * Appends an identifier to the current [AgentExecutionInfo] instance
         * with provided `id` as the trailing execution path element.
         *
         * @param id The identifier to append as the `partName` of the new [AgentExecutionInfo] instance.
         * @return A new [AgentExecutionInfo] instance with provided `id` as the trailing execution path element.
         */
        internal fun appendId(executionInfo: AgentExecutionInfo, id: String): AgentExecutionInfo {
            return AgentExecutionInfo(executionInfo, id)
        }

        private fun AgentExecutionInfo.appendRunId(runId: String?): AgentExecutionInfo =
            appendRunId(this, runId)

        private fun AgentExecutionInfo.appendId(id: String): AgentExecutionInfo =
            appendId(this, id)

        /**
         * Ends all unfinished spans that match the given predicate. If no predicate is provided, ends all spans.
         * Spans are closed from leaf nodes up to parent nodes to maintain a proper hierarchy.
         *
         * The optional [spanAdapter]'s [SpanAdapter.onBeforeSpanFinished] is invoked before
         * each span is closed so adapter post-processing remains consistent.
         *
         * @param spanCollector Span collector for retrieving active spans.
         * @param spanAdapter Optional span adapter for post-processing before span closure;
         * @param verbose Whether to log span closure details.
         * @param filter Optional filter for spans to end.
         */
        internal suspend fun endUnfinishedSpans(
            spanCollector: SpanCollector,
            spanAdapter: SpanAdapter?,
            verbose: Boolean = false,
            filter: ((GenAIAgentSpan) -> Boolean)? = null
        ) {
            val spansToEnd = spanCollector.getActiveSpans(filter)

            spansToEnd.forEach { spanNode ->
                try {
                    spanAdapter?.onBeforeSpanFinished(spanNode.span)
                    spanNode.span.end(verbose = verbose)
                    spanCollector.removeSpan(spanNode.span, spanNode.path)
                } catch (e: Exception) {
                    logger.warn(e) {
                        "${spanNode.span.logString} Failed to end span due to the error: ${e.message}"
                    }
                }
            }
        }

        private suspend fun endAndRemoveExecuteToolSpan(
            toolResult: JSONElement?,
            config: OpenTelemetryConfig,
            spanAdapter: SpanAdapter?,
            spanCollector: SpanCollector,
            eventContext: ToolCallEventContext,
            error: Throwable? = null,
        ) {
            val path = eventContext.executionInfo.appendRunId(eventContext.runId).appendId(eventContext.eventId)
            val span = spanCollector.getStartedSpan(
                executionInfo = path,
                eventId = eventContext.eventId,
                spanType = SpanType.EXECUTE_TOOL
            ) ?: return

            endExecuteToolSpan(
                span = span,
                toolResult = toolResult?.toKotlinxJsonElement(),
                error = error,
                verbose = config.isVerbose,
                spanAdapter = spanAdapter,
            )
            spanCollector.removeSpan(
                span = span,
                path = path
            )
        }

        private fun ToolRegistry.getMcpToolMeta(
            toolName: String,
        ): Map<String, String>? = tools.firstNotNullOfOrNull { tool ->
            if (tool.name == toolName && McpMetadataKeys.ToolId in tool.metadata) {
                tool.metadata
            } else {
                null
            }
        }

        //endregion Private Methods
    }
}
