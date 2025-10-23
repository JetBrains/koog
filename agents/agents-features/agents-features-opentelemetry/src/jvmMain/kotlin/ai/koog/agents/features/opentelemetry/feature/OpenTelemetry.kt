package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.core.agent.context.element.getAgentRunInfoElement
import ai.koog.agents.core.agent.context.element.getNodeInfoElement
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.pipeline.AIAgentGraphPipeline
import ai.koog.agents.core.utils.SerializationUtils
import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes
import ai.koog.agents.features.opentelemetry.event.AssistantMessageEvent
import ai.koog.agents.features.opentelemetry.event.ChoiceEvent
import ai.koog.agents.features.opentelemetry.event.ModerationResponseEvent
import ai.koog.agents.features.opentelemetry.event.SystemMessageEvent
import ai.koog.agents.features.opentelemetry.event.ToolMessageEvent
import ai.koog.agents.features.opentelemetry.event.UserMessageEvent
import ai.koog.agents.features.opentelemetry.span.CreateAgentSpan
import ai.koog.agents.features.opentelemetry.span.ExecuteToolSpan
import ai.koog.agents.features.opentelemetry.span.InferenceSpan
import ai.koog.agents.features.opentelemetry.span.InvokeAgentSpan
import ai.koog.agents.features.opentelemetry.span.NodeExecuteSpan
import ai.koog.agents.features.opentelemetry.span.SpanEndStatus
import ai.koog.agents.features.opentelemetry.span.SpanProcessor
import ai.koog.agents.utils.HiddenString
import ai.koog.prompt.message.Message
import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.api.trace.StatusCode
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
            val tracer = config.tracer
            val spanProcessor = SpanProcessor(tracer = tracer, verbose = config.isVerbose)
            val spanAdapter = config.spanAdapter

            // Stop all unfinished spans on a process finish to report them
            Runtime.getRuntime().addShutdownHook(
                Thread {
                    if (spanProcessor.spansCount > 1) {
                        logger.warn { "Unfinished spans detected. Please check your code for unclosed spans." }
                    }

                    logger.debug {
                        "Closing unended OpenTelemetry spans on process shutdown (size: ${spanProcessor.spansCount})"
                    }
                    spanProcessor.endUnfinishedSpans()
                }
            )

            //region Agent

            pipeline.interceptAgentStarting(this) { eventContext ->
                logger.debug { "Execute OpenTelemetry before agent started handler" }

                // Check if CreateAgentSpan is already added (when running the same agent >= 1 times)
                val createAgentSpanId = CreateAgentSpan.createId(eventContext.agent.id)

                val createAgentSpan = spanProcessor.getSpan(createAgentSpanId) ?: run {
                    val span = CreateAgentSpan(
                        model = eventContext.agent.agentConfig.model,
                        agentId = eventContext.agent.id
                    )

                    spanProcessor.startSpan(span)
                    span
                }

                // Create InvokeAgentSpan
                val invokeAgentSpan = InvokeAgentSpan(
                    parent = createAgentSpan,
                    provider = eventContext.agent.agentConfig.model.provider,
                    agentId = eventContext.agent.id,
                    runId = eventContext.runId,
                )

                spanAdapter?.onBeforeSpanStarted(invokeAgentSpan)
                spanProcessor.startSpan(invokeAgentSpan)
            }

            pipeline.interceptAgentCompleted(this) { eventContext ->
                logger.debug { "Execute OpenTelemetry agent finished handler" }

                // Make sure all spans inside InvokeAgentSpan are finished
                spanProcessor.endUnfinishedInvokeAgentSpans(
                    agentId = eventContext.agentId,
                    runId = eventContext.runId
                )

                // Find current InvokeAgentSpan
                val invokeAgentSpanId = InvokeAgentSpan.createId(
                    agentId = eventContext.agentId,
                    runId = eventContext.runId
                )

                val invokeAgentSpan = spanProcessor.getSpanCatching<InvokeAgentSpan>(invokeAgentSpanId)
                    ?: return@interceptAgentCompleted

                spanAdapter?.onBeforeSpanFinished(invokeAgentSpan)
                spanProcessor.endSpan(span = invokeAgentSpan)
            }

            pipeline.interceptAgentExecutionFailed(this) { eventContext ->
                logger.debug { "Execute OpenTelemetry agent run error handler" }

                // Make sure all spans inside InvokeAgentSpan are finished
                spanProcessor.endUnfinishedInvokeAgentSpans(
                    agentId = eventContext.agentId,
                    runId = eventContext.runId
                )

                // Finish current InvokeAgentSpan
                val invokeAgentSpanId = InvokeAgentSpan.createId(
                    agentId = eventContext.agentId,
                    runId = eventContext.runId
                )

                val invokeAgentSpan = spanProcessor.getSpanCatching<InvokeAgentSpan>(invokeAgentSpanId)
                    ?: return@interceptAgentExecutionFailed

                invokeAgentSpan.addAttribute(
                    attribute = SpanAttributes.Response.FinishReasons(
                        listOf(SpanAttributes.Response.FinishReasonType.Error)
                    )
                )

                spanAdapter?.onBeforeSpanFinished(invokeAgentSpan)
                spanProcessor.endSpan(
                    span = invokeAgentSpan,
                    spanEndStatus = SpanEndStatus(code = StatusCode.ERROR, description = eventContext.throwable.message)
                )
            }

            pipeline.interceptAgentClosing(this) { eventContext ->
                logger.debug { "Execute OpenTelemetry before agent closed handler" }

                val agentSpanId = CreateAgentSpan.createId(agentId = eventContext.agentId)

                val agentSpan = spanProcessor.getSpanCatching<CreateAgentSpan>(agentSpanId)
                    ?: return@interceptAgentClosing

                spanAdapter?.onBeforeSpanFinished(agentSpan)
                spanProcessor.endSpan(span = agentSpan)
            }

            //endregion Agent

            //region Node

            pipeline.interceptNodeExecutionStarting(this) { eventContext ->
                logger.debug { "Execute OpenTelemetry before node handler" }

                // Get current InvokeAgentSpan
                val agentRunInfoElement = getAgentRunInfoElement()
                    ?: return@interceptNodeExecutionStarting

                val invokeAgentSpanId = InvokeAgentSpan.createId(
                    agentId = agentRunInfoElement.agentId,
                    runId = agentRunInfoElement.runId
                )

                // Parent span
                val invokeAgentSpan = spanProcessor.getSpanCatching<InvokeAgentSpan>(invokeAgentSpanId)
                    ?: return@interceptNodeExecutionStarting

                // Create NodeExecuteSpan
                val nodeExecuteSpan = NodeExecuteSpan(
                    parent = invokeAgentSpan,
                    runId = eventContext.context.runId,
                    nodeName = eventContext.node.name,
                    nodeInput = getNodeData(eventContext.input, eventContext.inputType)
                )

                spanAdapter?.onBeforeSpanStarted(nodeExecuteSpan)
                spanProcessor.startSpan(nodeExecuteSpan)
            }

            pipeline.interceptNodeExecutionCompleted(this) { eventContext ->
                logger.debug { "Execute OpenTelemetry after node handler" }

                // Find current NodeExecuteSpan
                val agentRunInfoElement = getAgentRunInfoElement()
                    ?: return@interceptNodeExecutionCompleted

                // Finish existing NodeExecuteSpan
                val nodeExecuteSpanId = NodeExecuteSpan.createId(
                    agentId = agentRunInfoElement.agentId,
                    runId = agentRunInfoElement.runId,
                    nodeName = eventContext.node.name,
                )

                val nodeExecuteSpan = spanProcessor.getSpanCatching<NodeExecuteSpan>(nodeExecuteSpanId)
                    ?: return@interceptNodeExecutionCompleted

                val attributesToAdd = buildList {
                    getNodeData(eventContext.output, eventContext.outputType)?.let { outputString ->
                        add(CustomAttribute("koog.node.output", HiddenString(outputString)))
                    }
                }

                nodeExecuteSpan.addAttributes(attributesToAdd)

                spanAdapter?.onBeforeSpanFinished(nodeExecuteSpan)
                spanProcessor.endSpan(nodeExecuteSpan)
            }

            pipeline.interceptNodeExecutionFailed(this) { eventContext ->
                logger.debug { "Execute OpenTelemetry node execution error handler" }

                // Find current NodeExecuteSpan
                val agentRunInfoElement = getAgentRunInfoElement()
                    ?: return@interceptNodeExecutionFailed

                // Finish existing NodeExecuteSpan
                val nodeExecuteSpanId = NodeExecuteSpan.createId(
                    agentId = agentRunInfoElement.agentId,
                    runId = agentRunInfoElement.runId,
                    nodeName = eventContext.node.name,
                )

                val nodeExecuteSpan = spanProcessor.getSpanCatching<NodeExecuteSpan>(nodeExecuteSpanId)
                    ?: return@interceptNodeExecutionFailed

                spanAdapter?.onBeforeSpanFinished(nodeExecuteSpan)
                spanProcessor.endSpan(
                    span = nodeExecuteSpan,
                    spanEndStatus = SpanEndStatus(code = StatusCode.ERROR, description = eventContext.throwable.message)
                )
            }

            //endregion Node

            //region LLM Call

            pipeline.interceptLLMCallStarting(this) { eventContext ->
                logger.debug { "Execute OpenTelemetry before LLM call handler" }

                // Get current NodeExecuteSpan
                val agentRunInfoElement = getAgentRunInfoElement()
                    ?: return@interceptLLMCallStarting

                val nodeInfoElement = getNodeInfoElement()
                    ?: error("Unable to create LLM call span due to missing node info in context")

                val nodeExecuteSpanId = NodeExecuteSpan.createId(
                    agentId = agentRunInfoElement.agentId,
                    runId = agentRunInfoElement.runId,
                    nodeName = nodeInfoElement.nodeName,
                )

                val nodeExecuteSpan = spanProcessor.getSpanCatching<NodeExecuteSpan>(nodeExecuteSpanId)
                    ?: return@interceptLLMCallStarting

                val provider = eventContext.model.provider
                val runId = eventContext.runId
                val model = eventContext.model
                val temperature = eventContext.prompt.params.temperature ?: 0.0
                val promptId = eventContext.prompt.id

                val inferenceSpan = InferenceSpan(
                    provider = provider,
                    parent = nodeExecuteSpan,
                    runId = runId,
                    model = model,
                    promptId = promptId,
                    temperature = temperature,
                    maxTokens = eventContext.prompt.params.maxTokens,
                )

                // Add events to the InferenceSpan after the span is created
                val eventsFromMessages = eventContext.prompt.messages.map { message ->
                    when (message) {
                        is Message.System -> {
                            SystemMessageEvent(provider, message)
                        }

                        is Message.User -> {
                            UserMessageEvent(provider, message)
                        }

                        is Message.Assistant -> {
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
                spanProcessor.startSpan(inferenceSpan)
            }

            pipeline.interceptLLMCallCompleted(this) { eventContext ->
                logger.debug { "Execute OpenTelemetry after LLM call handler" }

                // Find current InferenceSpan
                val agentRunInfoElement = getAgentRunInfoElement()
                    ?: return@interceptLLMCallCompleted

                val nodeInfoElement = getNodeInfoElement()
                    ?: error("Unable to create LLM call span due to missing node info in context")

                val inferenceSpanId = InferenceSpan.createId(
                    agentId = agentRunInfoElement.agentId,
                    runId = agentRunInfoElement.runId,
                    nodeName = nodeInfoElement.nodeName,
                    promptId = eventContext.prompt.id
                )

                val inferenceSpan = spanProcessor.getSpanCatching<InferenceSpan>(inferenceSpanId)
                    ?: return@interceptLLMCallCompleted

                val provider = eventContext.model.provider

                // Add attributes to the InferenceSpan before finishing the span
                val attributesToAdd = buildList {
                    eventContext.responses.lastOrNull()?.let { message ->
                        message.metaInfo.inputTokensCount?.let { inputTokensCount ->
                            add(SpanAttributes.Usage.InputTokens(inputTokensCount))
                        }
                        message.metaInfo.outputTokensCount?.let { outputTokensCount ->
                            add(SpanAttributes.Usage.OutputTokens(outputTokensCount))
                        }
                        message.metaInfo.totalTokensCount?.let { totalTokensCount ->
                            add(SpanAttributes.Usage.TotalTokens(totalTokensCount))
                        }
                    }
                }

                inferenceSpan.addAttributes(attributesToAdd)

                // Add events to the InferenceSpan before finishing the span
                val eventsToAdd = buildList {
                    eventContext.responses.mapIndexed { index, message ->
                        when (message) {
                            is Message.Assistant -> {
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

                // Add attributes to InferenceSpan

                // Finish Reasons Attribute
                eventContext.responses.lastOrNull()?.let { message ->
                    val finishReasonsAttribute = when (message) {
                        is Message.Assistant -> {
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
                spanProcessor.endSpan(inferenceSpan)
            }

            //endregion LLM Call

            //region Tool Call

            pipeline.interceptToolCallStarting(this) { eventContext ->
                logger.debug { "Execute OpenTelemetry tool call handler" }

                // Get current NodeExecuteSpan
                val agentRunInfoElement = getAgentRunInfoElement()
                    ?: return@interceptToolCallStarting

                val nodeInfoElement = getNodeInfoElement()
                    ?: error("Unable to create tool call span due to missing node info in context")

                val nodeExecutionSpanId = NodeExecuteSpan.createId(
                    agentId = agentRunInfoElement.agentId,
                    runId = agentRunInfoElement.runId,
                    nodeName = nodeInfoElement.nodeName
                )

                val nodeExecuteSpan = spanProcessor.getSpanCatching<NodeExecuteSpan>(nodeExecutionSpanId)
                    ?: return@interceptToolCallStarting

                val executeToolSpan = ExecuteToolSpan(
                    parent = nodeExecuteSpan,
                    tool = eventContext.tool,
                    toolArgs = eventContext.toolArgs,
                    toolCallId = eventContext.toolCallId,
                )

                spanAdapter?.onBeforeSpanStarted(executeToolSpan)
                spanProcessor.startSpan(executeToolSpan)
            }

            pipeline.interceptToolCallCompleted(this) { eventContext ->
                logger.debug { "Execute OpenTelemetry tool result handler" }

                // Get current ExecuteToolSpan
                val agentRunInfoElement = getAgentRunInfoElement()
                    ?: return@interceptToolCallCompleted

                val nodeInfoElement = getNodeInfoElement()
                    ?: error("Unable to create tool call span due to missing node info in context")

                val executeToolSpanId = ExecuteToolSpan.createId(
                    agentId = agentRunInfoElement.agentId,
                    runId = agentRunInfoElement.runId,
                    nodeName = nodeInfoElement.nodeName,
                    toolName = eventContext.tool.name,
                )

                val executeToolSpan = spanProcessor.getSpanCatching<ExecuteToolSpan>(executeToolSpanId)
                    ?: return@interceptToolCallCompleted

                // End the ExecuteToolSpan span
                eventContext.result?.let { result ->
                    executeToolSpan.addAttribute(
                        attribute = SpanAttributes.Tool.OutputValue(
                            output = eventContext.tool.encodeResultToStringUnsafe(
                                result
                            )
                        )
                    )
                }

                spanAdapter?.onBeforeSpanFinished(span = executeToolSpan)
                spanProcessor.endSpan(span = executeToolSpan)
            }

            pipeline.interceptToolCallFailed(this) { eventContext ->
                logger.debug { "Execute OpenTelemetry tool call failure handler" }

                // Get current ExecuteToolSpan
                val agentRunInfoElement = getAgentRunInfoElement()
                    ?: return@interceptToolCallFailed

                val nodeInfoElement = getNodeInfoElement()
                    ?: error("Unable to create tool call span due to missing node info in context")

                val executeToolSpanId = ExecuteToolSpan.createId(
                    agentId = agentRunInfoElement.agentId,
                    runId = agentRunInfoElement.runId,
                    nodeName = nodeInfoElement.nodeName,
                    toolName = eventContext.tool.name
                )

                val executeToolSpan = spanProcessor.getSpanCatching<ExecuteToolSpan>(executeToolSpanId)
                    ?: return@interceptToolCallFailed

                executeToolSpan.addAttribute(
                    attribute = CommonAttributes.Error.Type(eventContext.throwable.message ?: "Unknown tool call error")
                )

                // End the ExecuteToolSpan span
                spanAdapter?.onBeforeSpanFinished(executeToolSpan)
                spanProcessor.endSpan(
                    span = executeToolSpan,
                    spanEndStatus = SpanEndStatus(code = StatusCode.ERROR, description = eventContext.throwable.message)
                )
            }

            pipeline.interceptToolValidationFailed(this) { eventContext ->
                logger.debug { "Execute OpenTelemetry tool validation error handler" }

                // Get current ExecuteToolSpan
                val agentRunInfoElement = getAgentRunInfoElement()
                    ?: return@interceptToolValidationFailed

                val nodeInfoElement = getNodeInfoElement()
                    ?: error("Unable to create tool call span due to missing node info in context")

                val executeToolSpanId = ExecuteToolSpan.createId(
                    agentId = agentRunInfoElement.agentId,
                    runId = agentRunInfoElement.runId,
                    nodeName = nodeInfoElement.nodeName,
                    toolName = eventContext.tool.name
                )

                val executeToolSpan = spanProcessor.getSpanCatching<ExecuteToolSpan>(executeToolSpanId)
                    ?: return@interceptToolValidationFailed

                executeToolSpan.addAttribute(
                    attribute = CommonAttributes.Error.Type(eventContext.error)
                )

                // End the ExecuteToolSpan span
                spanAdapter?.onBeforeSpanFinished(executeToolSpan)
                spanProcessor.endSpan(
                    span = executeToolSpan,
                    spanEndStatus = SpanEndStatus(code = StatusCode.ERROR, description = eventContext.error)
                )
            }

            //endregion Tool Call

            return openTelemetry
        }

        //region Private Methods

        /**
         * Retrieves the JSON representation of the given data based on its type.
         *
         * Note: See [KG-485](https://youtrack.jetbrains.com/issue/KG-485)
         *       Workaround for processing non-serializable [ReceivedToolResult] type in the node input/output.
         */
        private fun getNodeData(data: Any?, dataType: KType): String? {
            data ?: return null

            @OptIn(InternalAgentsApi::class)
            return SerializationUtils.encodeDataToStringOrDefault(data, dataType) {
                when (data) {
                    is ReceivedToolResult -> data.content
                    else -> data.toString()
                }
            }
        }

        //endregion Private Methods
    }
}
