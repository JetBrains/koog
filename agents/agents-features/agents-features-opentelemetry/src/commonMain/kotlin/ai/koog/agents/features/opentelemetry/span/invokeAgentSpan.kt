package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.features.opentelemetry.attribute.GenAIAttributes
import ai.koog.agents.features.opentelemetry.attribute.KoogAttributes
import ai.koog.agents.features.opentelemetry.extension.addCommonErrorAttributes
import ai.koog.agents.features.opentelemetry.extension.sumInputTokens
import ai.koog.agents.features.opentelemetry.extension.sumOutputTokens
import ai.koog.agents.features.opentelemetry.extension.systemMessages
import ai.koog.agents.features.opentelemetry.extension.toStatusData
import ai.koog.agents.features.opentelemetry.integration.SpanAdapter
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import io.opentelemetry.kotlin.factory.ContextFactory
import io.opentelemetry.kotlin.tracing.SpanKind
import io.opentelemetry.kotlin.tracing.Tracer

/**
 * Build and start a new Invoke Agent Span with necessary attributes.
 *
 * Add the necessary attributes for the Invoke Agent Span, according to the OpenTelemetry Semantic Convention:
 * https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-agent-spans/#invoke-agent-span
 *
 * Span attributes:
 * - gen_ai.operation.name (required)
 * - gen_ai.provider.name (required)
 * - gen_ai.agent.description (conditional)
 * - gen_ai.agent.id (conditional)
 * - gen_ai.agent.name (conditional)
 * - gen_ai.conversation.id (conditional)
 * - gen_ai.data_source.id (conditional)
 * - gen_ai.output.type (conditional/required)
 * - gen_ai.request.choice.count (conditional/required)
 * - gen_ai.request.model (conditional/required)
 * - gen_ai.request.seed (conditional/required)
 * - server.port (conditional/required)
 * - gen_ai.request.frequency_penalty (recommended)
 * - gen_ai.request.max_tokens (recommended)
 * - gen_ai.request.presence_penalty (recommended)
 * - gen_ai.request.stop_sequences (recommended)
 * - gen_ai.request.temperature (recommended)
 * - gen_ai.request.top_p (recommended)
 * - gen_ai.input.messages (recommended)
 * - gen_ai.system_instructions (recommended)
 * - gen_ai.tool.definitions (recommended)
 * - server.address (recommended)
 *
 * Custom attributes:
 * - koog.event.id
 *
 * @param tracer The tracer instance to use for creating the span.
 * @param contextFactory The context factory to use for creating the span context.
 * @param parentSpan The parent span for the new span.
 * @param id The unique identifier for the span.
 * @param model The model used for inference.
 * @param runId The unique identifier for the run.
 * @param agentId The unique identifier for the agent.
 * @param llmParams The parameters for the LLM request.
 * @param messages The list of messages used in the inference.
 * @param tools The list of tools available for the inference.
 * @param spanAdapter Optional span adapter for customizing the span behavior.
 */
internal fun startInvokeAgentSpan(
    tracer: Tracer,
    contextFactory: ContextFactory,
    parentSpan: GenAIAgentSpan?,
    id: String,
    model: LLModel,
    runId: String,
    agentId: String,
    llmParams: LLMParams,
    messages: List<Message>,
    tools: List<ToolDescriptor>,
    spanAdapter: SpanAdapter? = null,
): GenAIAgentSpan {
    val builder = GenAIAgentSpanBuilder(
        spanType = SpanType.INVOKE_AGENT,
        parentSpan = parentSpan,
        id = id,
        kind = SpanKind.CLIENT,
        name = "${GenAIAttributes.Operation.OperationNameType.INVOKE_AGENT.id} $agentId",
    )

    // gen_ai.operation.name
    builder.addAttribute(GenAIAttributes.Operation.Name(GenAIAttributes.Operation.OperationNameType.INVOKE_AGENT))
        // gen_ai.provider.name
        .addAttribute(GenAIAttributes.Provider.Name(model.provider))
        // gen_ai.agent.description - Ignore. Not supported in Koog
        // gen_ai.agent.id
        .addAttribute(GenAIAttributes.Agent.Id(agentId))
        // gen_ai.agent.name - Ignore. Not supported in Koog
        // gen_ai.conversation.id
        .addAttribute(GenAIAttributes.Conversation.Id(runId))
        // gen_ai.data_source.id - Ignore. Not supported in Koog
        // gen_ai.output.type
        .addAttribute(
            GenAIAttributes.Output.Type(
                if (llmParams.schema != null) {
                    GenAIAttributes.Output.OutputType.JSON
                } else {
                    GenAIAttributes.Output.OutputType.TEXT
                }
            )
        )

    // gen_ai.request.choice.count
    llmParams.numberOfChoices?.let { number ->
        builder.addAttribute(GenAIAttributes.Request.Choice.Count(number))
    }

    // gen_ai.request.model
    builder.addAttribute(GenAIAttributes.Request.Model(model))

    // gen_ai.request.seed - Ignore. Not supported in Koog
    // server.port - Ignore. Not supported in Koog
    // gen_ai.request.frequency_penalty - Ignore. Not supported in Koog
    // gen_ai.request.max_tokens
    llmParams.maxTokens?.let { maxTokens ->
        builder.addAttribute(GenAIAttributes.Request.MaxTokens(maxTokens))
    }

    // gen_ai.request.presence_penalty - Ignore. Not supported in Koog
    // gen_ai.request.stop_sequences - Ignore. Not supported in Koog
    // gen_ai.request.temperature
    llmParams.temperature?.let { temperature ->
        builder.addAttribute(GenAIAttributes.Request.Temperature(temperature))
    }

    // gen_ai.request.top_p - Ignore. Not supported in Koog
    // server.address - Ignore. Not supported in Koog
    // gen_ai.input.messages
    if (messages.isNotEmpty()) {
        builder.addAttribute(GenAIAttributes.Input.Messages(messages))
    }

    // gen_ai.system_instructions
    val systemMessages = messages.systemMessages()
    if (systemMessages.isNotEmpty()) {
        builder.addAttribute(GenAIAttributes.SystemInstructions(systemMessages))
    }

    // gen_ai.tool.definitions
    if (tools.isNotEmpty()) {
        builder.addAttribute(GenAIAttributes.Tool.Definitions(tools))
    }

    builder.addAttribute(KoogAttributes.Koog.Event.Id(id))

    val span = builder.buildAndStart(tracer, contextFactory)
    spanAdapter?.onBeforeSpanStarted(span)
    return span
}

/**
 * End Invoke Agent Span and set final attributes. The provided [spanAdapter] is invoked via
 * [SpanAdapter.onBeforeSpanFinished] after all attributes are set and immediately before the
 * underlying span is ended.
 *
 * Add the necessary attributes for the Invoke Agent Span, according to the OpenTelemetry Semantic Convention:
 * https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-agent-spans/#invoke-agent-span
 *
 * Span attribute:
 * - error.type (conditional)
 * - gen_ai.response.finish_reasons (recommended)
 * - gen_ai.response.id (recommended)
 * - gen_ai.response.model (recommended)
 * - gen_ai.usage.input_tokens (recommended)
 * - gen_ai.usage.output_tokens (recommended)
 * - gen_ai.output.messages (recommended)
 *
 * @param span The span to end.
 * @param messages The list of messages used in the inference.
 * @param model The model used for inference.
 * @param error The error that occurred during inference if any.
 * @param verbose Whether to log verbose information.
 * @param spanAdapter Optional span adapter for customizing the span behavior.
 */
internal fun endInvokeAgentSpan(
    span: GenAIAgentSpan,
    messages: List<Message>,
    model: LLModel,
    error: Throwable? = null,
    verbose: Boolean = false,
    spanAdapter: SpanAdapter? = null,
) {
    check(span.type == SpanType.INVOKE_AGENT) {
        "${span.logString} Expected to end span type of type: <${SpanType.INVOKE_AGENT}>, but received span of type: <${span.type}>"
    }

    // error.type
    span.addCommonErrorAttributes(error)

    // gen_ai.response.finish_reasons - Ignore. Not supported in Koog
    // gen_ai.response.id - Ignore. Not supported in Koog
    // gen_ai.response.model
    span.addAttribute(GenAIAttributes.Response.Model(model))

    // gen_ai.usage.input_tokens
    span.addAttribute(GenAIAttributes.Usage.InputTokens(messages.sumInputTokens()))

    // gen_ai.usage.output_tokens
    span.addAttribute(GenAIAttributes.Usage.OutputTokens(messages.sumOutputTokens()))

    // gen_ai.output.messages
    if (messages.isNotEmpty()) {
        span.addAttribute(GenAIAttributes.Output.Messages(messages))
    }

    spanAdapter?.onBeforeSpanFinished(span)
    span.end(error.toStatusData(), verbose)
}
