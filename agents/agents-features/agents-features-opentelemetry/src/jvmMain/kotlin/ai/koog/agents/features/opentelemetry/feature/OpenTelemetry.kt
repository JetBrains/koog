package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.pipeline.AIAgentGraphPipeline
import ai.koog.agents.core.utils.SerializationUtils
import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes
import ai.koog.agents.features.opentelemetry.event.AssistantMessageEvent
import ai.koog.agents.features.opentelemetry.event.ChoiceEvent
import ai.koog.agents.features.opentelemetry.event.ModerationResponseEvent
import ai.koog.agents.features.opentelemetry.event.SystemMessageEvent
import ai.koog.agents.features.opentelemetry.event.ToolMessageEvent
import ai.koog.agents.features.opentelemetry.event.UserMessageEvent
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
import ai.koog.prompt.message.Message
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.reflect.KType

/**
 * Represents the OpenTelemetry integration feature for tracking and managing spans and contexts
 * within the AI Agent framework. This class manages the lifecycle of spans for various operations,
 * including agent executions, node processing, LLM calls, and tool calls.
 */
public class OpenTelemetry {

    /**
     * Companion object implementing agent feature, handling [OpenTelemetry] creation and installation.
     */
    public companion object Feature : AIAgentGraphFeature<OpenTelemetryConfig, OpenTelemetry> {

        private val logger = KotlinLogging.logger { }

        override val key: AIAgentStorageKey<OpenTelemetry> = AIAgentStorageKey("agents-features-opentelemetry")

        override fun createInitialConfig(): OpenTelemetryConfig {
            return OpenTelemetryConfig()
        }

        override fun install(
            config: OpenTelemetryConfig,
            pipeline: AIAgentGraphPipeline,
        ): OpenTelemetry {
            val openTelemetry = OpenTelemetry()
            val spanCollector = SpanCollector()
            val spanAdapter = config.spanAdapter

            val tracer = config.tracer

            //region Agent

            pipeline.interceptAgentStarting(this) intercept@{ eventContext ->
                logger.debug { "Execute OpenTelemetry before agent started handler" }

                val messages = eventContext.agent.agentConfig.prompt.messages.toList()
                val tools = (eventContext.agent as? GraphAIAgent<*, *>)?.toolRegistry?.tools?.map { it.descriptor }?.toList()
                    ?: emptyList()

                // Create CreateAgentSpan
                val createAgentSpan = startCreateAgentSpan(
                    tracer = tracer,
                    parentSpan = null,
                    id = eventContext.eventId,
                    model = eventContext.agent.agentConfig.model,
                    agentId = eventContext.context.agentId,
                    messages = messages
                )

                spanAdapter?.onBeforeSpanStarted(createAgentSpan)
                spanCollector.collectSpan(
                    span = createAgentSpan,
                    path = eventContext.executionInfo
                )

                // Create InvokeAgentSpan
                val invokeAgentSpan = startInvokeAgentSpan(
                    tracer = tracer,
                    parentSpan = createAgentSpan,
                    id = eventContext.runId,
                    model = eventContext.agent.agentConfig.model,
                    agentId = eventContext.agent.id,
                    runId = eventContext.runId,
                    llmParams = eventContext.agent.agentConfig.prompt.params,
                    messages = messages,
                    tools = tools
                )

                spanAdapter?.onBeforeSpanStarted(invokeAgentSpan)
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

                spanAdapter?.onBeforeSpanFinished(invokeAgentSpan)
                endInvokeAgentSpan(
                    span = invokeAgentSpan,
                    messages = eventContext.context.config.prompt.messages.toList(),
                    model = eventContext.context.config.model,
                    verbose = config.isVerbose
                )
                spanCollector.removeSpan(
                    span = invokeAgentSpan,
                    path = patchedExecutionInfo
                )
            }

            pipeline.interceptAgentExecutionFailed(this) intercept@{ eventContext ->
                logger.debug { "Execute OpenTelemetry agent run error handler" }

                // Stop all unfinished spans, except InvokeAgentSpan and AgentCreateSpan
                endUnfinishedSpans(spanCollector, config.isVerbose) { span ->
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

                invokeAgentSpan.addAttribute(
                    attribute = SpanAttributes.Response.FinishReasons(
                        listOf(SpanAttributes.Response.FinishReasonType.Error)
                    )
                )

                spanAdapter?.onBeforeSpanFinished(invokeAgentSpan)
                endInvokeAgentSpan(
                    span = invokeAgentSpan,
                    messages = eventContext.context.config.prompt.messages.toList(),
                    model = eventContext.context.config.model,
                    error = eventContext.throwable,
                    verbose = config.isVerbose
                )
                spanCollector.removeSpan(
                    span = invokeAgentSpan,
                    path = patchedExecutionInfo
                )
            }

            pipeline.interceptAgentClosing(this) intercept@{ eventContext ->
                logger.debug { "Execute OpenTelemetry before agent closed handler" }

                // Stop all unfinished spans, except the AgentCreateSpan
                endUnfinishedSpans(spanCollector, config.isVerbose) { span ->
                    span.type != SpanType.CREATE_AGENT
                }

                // Stop agent create span
                val agentSpan = spanCollector.getStartedSpan(
                    executionInfo = eventContext.executionInfo,
                    eventId = eventContext.eventId,
                    spanType = SpanType.CREATE_AGENT
                ) ?: return@intercept

                spanAdapter?.onBeforeSpanFinished(agentSpan)
                endCreateAgentSpan(
                    span = agentSpan,
                    verbose = config.isVerbose
                )

                spanCollector.removeSpan(
                    span = agentSpan,
                    path = eventContext.executionInfo
                )

                // Just in case we miss some spans, stop them as well
                if (spanCollector.activeSpansCount > 0) {
                    logger.warn { "Found <${spanCollector.activeSpansCount}> active span(s) after agent closing. Stopping them." }
                    endUnfinishedSpans(spanCollector, config.isVerbose)
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
                    parentSpan = parentSpan,
                    id = eventContext.eventId,
                    runId = eventContext.context.runId,
                    strategyName = eventContext.strategy.name
                )

                spanAdapter?.onBeforeSpanStarted(strategySpan)
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

                spanAdapter?.onBeforeSpanFinished(strategySpan)
                endStrategySpan(span = strategySpan, verbose = config.isVerbose)
                spanCollector.removeSpan(
                    span = strategySpan,
                    path = patchedExecutionInfo
                )
            }

            //endregion Strategy

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

                val nodeInput = nodeDataToString(eventContext.input, eventContext.inputType)

                val nodeExecuteSpan = startNodeExecuteSpan(
                    tracer = tracer,
                    parentSpan = parentSpan,
                    id = eventContext.eventId,
                    runId = eventContext.context.runId,
                    nodeId = eventContext.node.id,
                    nodeInput = nodeInput
                )

                spanAdapter?.onBeforeSpanStarted(nodeExecuteSpan)
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

                val nodeOutput = nodeDataToString(eventContext.output, eventContext.outputType)

                spanAdapter?.onBeforeSpanFinished(nodeExecuteSpan)
                endNodeExecuteSpan(
                    span = nodeExecuteSpan,
                    nodeOutput = nodeOutput,
                    verbose = config.isVerbose
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

                spanAdapter?.onBeforeSpanFinished(nodeExecuteSpan)
                endNodeExecuteSpan(
                    span = nodeExecuteSpan,
                    nodeOutput = null,
                    error = eventContext.throwable,
                    verbose = config.isVerbose
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

                val subgraphInput = nodeDataToString(eventContext.input, eventContext.inputType)

                val subgraphExecuteSpan = startSubgraphExecuteSpan(
                    tracer = tracer,
                    parentSpan = parentSpan,
                    id = eventContext.eventId,
                    runId = eventContext.context.runId,
                    subgraphId = eventContext.subgraph.id,
                    subgraphInput = subgraphInput
                )

                spanAdapter?.onBeforeSpanStarted(subgraphExecuteSpan)
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

                val subgraphOutput = nodeDataToString(eventContext.output, eventContext.outputType)

                spanAdapter?.onBeforeSpanFinished(subgraphExecuteSpan)
                endSubgraphExecuteSpan(
                    span = subgraphExecuteSpan,
                    subgraphOutput = subgraphOutput,
                    verbose = config.isVerbose
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

                spanAdapter?.onBeforeSpanFinished(subgraphExecuteSpan)
                endSubgraphExecuteSpan(
                    span = subgraphExecuteSpan,
                    subgraphOutput = null,
                    error = eventContext.throwable,
                    verbose = config.isVerbose
                )
                spanCollector.removeSpan(
                    span = subgraphExecuteSpan,
                    path = patchedExecutionInfo
                )
            }

            //endregion Subgraph

            //region LLM Call

            pipeline.interceptLLMCallStarting(this) intercept@{ eventContext ->
                logger.debug { "Execute OpenTelemetry before LLM call handler" }

                val provider = eventContext.model.provider
                val patchedExecutionInfo = eventContext.executionInfo
                    .appendRunId(eventContext.runId)
                    .appendId(eventContext.eventId)

                val parentSpan = spanCollector.getParentSpanForEvent(
                    executionInfo = patchedExecutionInfo,
                ) ?: return@intercept

                val messages = eventContext.prompt.messages.toList()

                val inferenceSpan = startInferenceSpan(
                    tracer = tracer,
                    parentSpan = parentSpan,
                    id = eventContext.eventId,
                    provider = eventContext.model.provider,
                    runId = eventContext.runId,
                    model = eventContext.model,
                    messages = messages,
                    llmParams = eventContext.prompt.params,
                    tools = eventContext.tools
                )

                // Add events to the InferenceSpan after the span is created
                val eventsFromMessages = messages.map { message ->
                    when (message) {
                        is Message.System -> {
                            SystemMessageEvent(provider, message)
                        }

                        is Message.User -> {
                            UserMessageEvent(provider, message)
                        }

                        is Message.Assistant, is Message.Reasoning -> {
                            AssistantMessageEvent(provider, message)
                        }

                        is Message.Tool.Call -> {
                            ChoiceEvent(provider, message, arguments = message.contentJson)
                        }

                        is Message.Tool.Result -> {
                            ToolMessageEvent(
                                provider = provider,
                                toolCallId = message.id,
                                content = message.content
                            )
                        }
                    }
                }

                inferenceSpan.addEvents(eventsFromMessages)

                // Start span
                spanAdapter?.onBeforeSpanStarted(inferenceSpan)
                spanCollector.collectSpan(
                    span = inferenceSpan,
                    path = patchedExecutionInfo
                )
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

                val provider = eventContext.model.provider

                // Add events to the InferenceSpan before finishing the span
                val eventsToAdd = buildList {
                    eventContext.responses.mapIndexed { index, message ->
                        when (message) {
                            is Message.Assistant, is Message.Reasoning -> {
                                add(AssistantMessageEvent(provider, message))
                            }

                            is Message.Tool.Call -> {
                                add(ChoiceEvent(provider, message, arguments = message.contentJson, index = index))
                            }
                        }
                    }

                    eventContext.moderationResponse?.let { response ->
                        add(ModerationResponseEvent(provider, response))
                    }
                }

                inferenceSpan.addEvents(eventsToAdd)

                // Finish Reasons Attribute
                eventContext.responses.lastOrNull()?.let { message ->
                    val finishReasonsAttribute = when (message) {
                        is Message.Assistant, is Message.Reasoning -> {
                            SpanAttributes.Response.FinishReasons(reasons = listOf(SpanAttributes.Response.FinishReasonType.Stop))
                        }

                        is Message.Tool.Call -> {
                            SpanAttributes.Response.FinishReasons(reasons = listOf(SpanAttributes.Response.FinishReasonType.ToolCalls))
                        }
                    }

                    inferenceSpan.addAttribute(finishReasonsAttribute)
                }

                // Stop InferenceSpan
                spanAdapter?.onBeforeSpanFinished(inferenceSpan)
                endInferenceSpan(
                    span = inferenceSpan,
                    messages = eventContext.responses,
                    model = eventContext.model,
                    verbose = config.isVerbose
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

                val patchedExecutionInfo = eventContext.executionInfo
                    .appendRunId(eventContext.runId)
                    .appendId(eventContext.eventId)

                val parentSpan = spanCollector.getParentSpanForEvent(
                    executionInfo = patchedExecutionInfo,
                ) ?: return@intercept

                val executeToolSpan = startExecuteToolSpan(
                    tracer = tracer,
                    parentSpan = parentSpan,
                    id = eventContext.eventId,
                    toolName = eventContext.toolName,
                    toolArgs = eventContext.toolArgs,
                    toolDescription = eventContext.toolDescription,
                    toolCallId = eventContext.toolCallId
                )

                spanAdapter?.onBeforeSpanStarted(executeToolSpan)
                spanCollector.collectSpan(executeToolSpan, patchedExecutionInfo)
            }

            pipeline.interceptToolCallCompleted(this) intercept@{ eventContext ->
                logger.debug { "Execute OpenTelemetry tool result handler" }

                // Get the current ExecuteToolSpan
                val patchedExecutionInfo = eventContext.executionInfo
                    .appendRunId(eventContext.runId)
                    .appendId(eventContext.eventId)

                val executeToolSpan = spanCollector.getStartedSpan(
                    executionInfo = patchedExecutionInfo,
                    eventId = eventContext.eventId,
                    spanType = SpanType.EXECUTE_TOOL
                ) ?: return@intercept

                eventContext.toolDescription?.let { toolDescription ->
                    executeToolSpan.addAttribute(
                        // gen_ai.tool.description
                        attribute = SpanAttributes.Tool.Description(description = toolDescription)
                    )
                }

                spanAdapter?.onBeforeSpanFinished(span = executeToolSpan)

                val toolResult = eventContext.toolResult ?: kotlinx.serialization.json.JsonObject(emptyMap())
                endExecuteToolSpan(
                    span = executeToolSpan,
                    toolResult = toolResult,
                    verbose = config.isVerbose
                )
                spanCollector.removeSpan(
                    span = executeToolSpan,
                    path = patchedExecutionInfo
                )
            }

            pipeline.interceptToolCallFailed(this) intercept@{ eventContext ->
                logger.debug { "Execute OpenTelemetry tool call failure handler" }

                // Get the current ExecuteToolSpan using executionInfo.path()
                val patchedExecutionInfo = eventContext.executionInfo
                    .appendRunId(eventContext.runId)
                    .appendId(eventContext.eventId)

                val executeToolSpan = spanCollector.getStartedSpan(
                    executionInfo = patchedExecutionInfo,
                    eventId = eventContext.eventId,
                    spanType = SpanType.EXECUTE_TOOL
                ) ?: return@intercept

                eventContext.toolDescription?.let { toolDescription ->
                    executeToolSpan.addAttribute(
                        // gen_ai.tool.description
                        attribute = SpanAttributes.Tool.Description(description = toolDescription)
                    )
                }

                executeToolSpan.addAttribute(
                    attribute = CommonAttributes.Error.Type(eventContext.message)
                )

                // End the ExecuteToolSpan span
                spanAdapter?.onBeforeSpanFinished(executeToolSpan)
                val toolResult = kotlinx.serialization.json.JsonObject(emptyMap())
                endExecuteToolSpan(
                    span = executeToolSpan,
                    toolResult = toolResult,
                    error = eventContext.error,
                    verbose = config.isVerbose
                )
                spanCollector.removeSpan(
                    span = executeToolSpan,
                    path = patchedExecutionInfo
                )
            }

            pipeline.interceptToolValidationFailed(this) intercept@{ eventContext ->
                logger.debug { "Execute OpenTelemetry tool validation error handler" }

                // Get the current ExecuteToolSpan using executionInfo.path()
                val patchedExecutionInfo = eventContext.executionInfo
                    .appendRunId(eventContext.runId)
                    .appendId(eventContext.eventId)

                val executeToolSpan = spanCollector.getStartedSpan(
                    executionInfo = patchedExecutionInfo,
                    eventId = eventContext.eventId,
                    spanType = SpanType.EXECUTE_TOOL
                ) ?: return@intercept

                eventContext.toolDescription?.let { toolDescription ->
                    executeToolSpan.addAttribute(
                        // gen_ai.tool.description
                        attribute = SpanAttributes.Tool.Description(description = toolDescription)
                    )
                }

                executeToolSpan.addAttribute(
                    attribute = CommonAttributes.Error.Type(eventContext.message)
                )

                // End the ExecuteToolSpan span
                spanAdapter?.onBeforeSpanFinished(executeToolSpan)
                endExecuteToolSpan(
                    span = executeToolSpan,
                    toolResult = null,
                    error = eventContext.error,
                    verbose = config.isVerbose
                )
                spanCollector.removeSpan(
                    span = executeToolSpan,
                    path = patchedExecutionInfo
                )
            }

            //endregion Tool Call

            return openTelemetry
        }

        //region Private Methods

        /**
         * Retrieves the [String] representation of the given data based on its type.
         */
        private fun nodeDataToString(data: Any?, dataType: KType): String? {
            data ?: return null

            @OptIn(InternalAgentsApi::class)
            return SerializationUtils.encodeDataToStringOrDefault(data, dataType)
        }

        /**
         * Retrieves the parent span for a given event context if available.
         * Checks if the event ID matches any child spans of the parent span node.
         *
         * @param executionInfo The execution information of the current event context.
         * @return The parent span associated with the event if found, or null if no matching parent is found.
         */
        private fun SpanCollector.getParentSpanForEvent(
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
         *              e.g., span wihh type [ai.koog.agents.features.opentelemetry.span.SpanType.CREATE_AGENT].
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
         * Ends all unfinished spans that match the given predicate.
         * If no predicate is provided, ends all spans.
         * Spans are closed from leaf nodes up to parent nodes to maintain a proper hierarchy.
         *
         * @param filter Optional filter for spans to end.
         */
        internal fun endUnfinishedSpans(
            spanCollector: SpanCollector,
            verbose: Boolean = false,
            filter: ((GenAIAgentSpan) -> Boolean)? = null
        ) {
            val spansToEnd = spanCollector.getActiveSpans(filter)

            spansToEnd.forEach { spanNode ->
                try {
                    spanNode.span.end(verbose = verbose)
                    spanCollector.removeSpan(spanNode.span, spanNode.path)
                } catch (e: Exception) {
                    logger.warn(e) {
                        "${spanNode.span.logString} Failed to end span due to the error: ${e.message}"
                    }
                }
            }
        }

        //endregion Private Methods
    }
}
