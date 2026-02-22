package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.agents.features.opentelemetry.attribute.KoogAttributes
import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes
import ai.koog.agents.features.opentelemetry.extension.toSpanEndStatus
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.Tracer

/**
 * Build and start a new Invoke Agent Span with necessary attributes.
 *
 * Add the necessary attributes for the Invoke Agent Span, according to the Open Telemetry Semantic Convention:
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
 */
internal fun startInvokeAgentSpan(
    tracer: Tracer,
    parentSpan: GenAIAgentSpan?,
    id: String,
    model: LLModel,
    runId: String,
    agentId: String,
    llmParams: LLMParams,
    messages: List<Message>,
    tools: List<ToolDescriptor>
): GenAIAgentSpan {
    val builder = GenAIAgentSpanBuilder(
        spanType = SpanType.INVOKE_AGENT,
        parentSpan = parentSpan,
        id = id,
        kind = SpanKind.CLIENT,
        name = "${SpanAttributes.Operation.OperationNameType.INVOKE_AGENT.id} $agentId",
    )

    // gen_ai.operation.name
    builder.addAttribute(SpanAttributes.Operation.Name(SpanAttributes.Operation.OperationNameType.INVOKE_AGENT))
        // gen_ai.provider.name
        .addAttribute(SpanAttributes.Provider.Name(model.provider))
        // gen_ai.agent.description - Ignore. Not supported in Koog
        // gen_ai.agent.id
        .addAttribute(SpanAttributes.Agent.Id(agentId))
        // gen_ai.agent.name - Ignore. Not supported in Koog
        // gen_ai.conversation.id
        .addAttribute(SpanAttributes.Conversation.Id(runId))
        // gen_ai.data_source.id - Ignore. Not supported in Koog
        // gen_ai.output.type
        .addAttribute(
            SpanAttributes.Output.Type(
                if (llmParams.schema != null) {
                    SpanAttributes.Output.OutputType.JSON
                } else {
                    SpanAttributes.Output.OutputType.TEXT
                }
            )
        )

    // gen_ai.request.choice.count
    llmParams.numberOfChoices?.let { number ->
        builder.addAttribute(SpanAttributes.Request.Choice.Count(number))
    }

    // gen_ai.request.model
    builder.addAttribute(SpanAttributes.Request.Model(model))

    // gen_ai.request.seed - Ignore. Not supported in Koog
    // server.port - Ignore. Not supported in Koog
    // gen_ai.request.frequency_penalty - Ignore. Not supported in Koog
    // gen_ai.request.max_tokens
    llmParams.maxTokens?.let { maxTokens ->
        builder.addAttribute(SpanAttributes.Request.MaxTokens(maxTokens))
    }

    // gen_ai.request.presence_penalty - Ignore. Not supported in Koog
    // gen_ai.request.stop_sequences - Ignore. Not supported in Koog
    // gen_ai.request.temperature
    llmParams.temperature?.let { temperature ->
        builder.addAttribute(SpanAttributes.Request.Temperature(temperature))
    }

    // gen_ai.request.top_p - Ignore. Not supported in Koog
    // server.address - Ignore. Not supported in Koog
    // gen_ai.input.messages
    if (messages.isNotEmpty()) {
        builder.addAttribute(SpanAttributes.Input.Messages(messages))
    }

    // gen_ai.system_instructions
    val systemMessages = messages.filterIsInstance<Message.System>()
    if (systemMessages.isNotEmpty()) {
        builder.addAttribute(SpanAttributes.SystemInstructions(systemMessages))
    }

    // gen_ai.tool.definitions
    if (tools.isNotEmpty()) {
        builder.addAttribute(SpanAttributes.Tool.Definitions(tools))
    }

    builder.addAttribute(KoogAttributes.Koog.Event.Id(id))

    return builder.buildAndStart(tracer)
}

/**
 * End Invoke Agent Span and set final attributes.
 *
 * Add the necessary attributes for the Invoke Agent Span, according to the Open Telemetry Semantic Convention:
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
 */
internal fun endInvokeAgentSpan(
    span: GenAIAgentSpan,
    messages: List<Message>,
    model: LLModel,
    error: Throwable? = null,
    verbose: Boolean = false
) {
    check(span.type == SpanType.INVOKE_AGENT) {
        "${span.logString} Expected to end span type of type: <${SpanType.INVOKE_AGENT}>, but received span of type: <${span.type}>"
    }

    // error.type
    error?.javaClass?.typeName?.let { typeName ->
        span.addAttribute(CommonAttributes.Error.Type(typeName))
    }

    // gen_ai.response.finish_reasons - Ignore. Not supported in Koog
    // gen_ai.response.id - Ignore. Not supported in Koog
    // gen_ai.response.model
    span.addAttribute(SpanAttributes.Response.Model(model))

    // gen_ai.usage.input_tokens
    span.addAttribute(
        SpanAttributes.Usage.InputTokens(
            messages.filterIsInstance<Message.Response>().sumOf { message -> message.metaInfo.inputTokensCount ?: 0 }
        )
    )

    // gen_ai.usage.output_tokens
    span.addAttribute(
        SpanAttributes.Usage.OutputTokens(
            messages.filterIsInstance<Message.Response>().sumOf { message -> message.metaInfo.outputTokensCount ?: 0 }
        )
    )

    // gen_ai.output.messages
    if (messages.isNotEmpty()) {
        span.addAttribute(SpanAttributes.Output.Messages(messages))
    }

    span.end(error.toSpanEndStatus(), verbose)
}
