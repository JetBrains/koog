package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.agents.features.opentelemetry.attribute.GenAIAttributes
import ai.koog.agents.features.opentelemetry.attribute.KoogAttributes
import ai.koog.agents.features.opentelemetry.extension.toSpanEndStatus
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.Tracer

/**
 * Build and start a new Inference Span with necessary attributes.
 *
 * Add the necessary attributes for the Inference Span according to the Open Telemetry Semantic Convention:
 * https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-spans/#inference
 *
 * Span attributes:
 * - gen_ai.operation.name (required)
 * - gen_ai.provider.name (required)
 * - gen_ai.conversation.id (conditional)
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
 * - gen_ai.request.top_k (recommended)
 * - gen_ai.request.top_p (recommended)
 * - gen_ai.input.messages (recommended)
 * - gen_ai.system_instructions (recommended)
 * - gen_ai.tool.definitions (recommended)
 * - server.address (recommended)
 *
 * Custom attributes:
 * - koog.event.id
 */
internal fun startInferenceSpan(
    tracer: Tracer,
    parentSpan: GenAIAgentSpan?,
    id: String,
    provider: LLMProvider,
    runId: String,
    model: LLModel,
    messages: List<Message>,
    llmParams: LLMParams,
    tools: List<ToolDescriptor>
): GenAIAgentSpan {
    val builder = GenAIAgentSpanBuilder(
        spanType = SpanType.INFERENCE,
        parentSpan = parentSpan,
        id = id,
        kind = SpanKind.CLIENT,
        name = "${GenAIAttributes.Operation.OperationNameType.CHAT.id} ${model.id}",
    )
        // gen_ai.operation.name
        .addAttribute(GenAIAttributes.Operation.Name(GenAIAttributes.Operation.OperationNameType.CHAT))
        // gen_ai.provider.name
        .addAttribute(GenAIAttributes.Provider.Name(provider))
        // gen_ai.conversation.id
        .addAttribute(GenAIAttributes.Conversation.Id(runId))
        // gen_ai.output.type
        .addAttribute(
            GenAIAttributes.Output.Type(
                type = if (llmParams.schema != null) {
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
    llmParams.maxTokens?.let {
        builder.addAttribute(GenAIAttributes.Request.MaxTokens(it))
    }

    // gen_ai.request.presence_penalty - Ignore. Not supported in Koog
    // gen_ai.request.stop_sequences - Ignore. Not supported in Koog
    // gen_ai.request.temperature
    llmParams.temperature?.let {
        builder.addAttribute(GenAIAttributes.Request.Temperature(it))
    }

    // gen_ai.request.top_k - Ignore. Not supported in Koog
    // gen_ai.request.top_p - Ignore. Not supported in Koog
    // server.address - Ignore. Not supported in Koog
    // gen_ai.input.messages
    if (messages.isNotEmpty()) {
        builder.addAttribute(GenAIAttributes.Input.Messages(messages))
    }

    // gen_ai.system_instructions
    val systemMessages = messages.filterIsInstance<Message.System>()
    if (systemMessages.isNotEmpty()) {
        builder.addAttribute(GenAIAttributes.SystemInstructions(systemMessages))
    }

    // gen_ai.tool.definitions
    if (tools.isNotEmpty()) {
        builder.addAttribute(GenAIAttributes.Tool.Definitions(tools))
    }

    builder.addAttribute(KoogAttributes.Koog.Event.Id(id))

    return builder.buildAndStart(tracer)
}

/**
 * End Inference Span and set final attributes.
 *
 * Add the necessary attributes for the Inference Span according to the Open Telemetry Semantic Convention:
 * https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-spans/#inference
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
internal fun endInferenceSpan(
    span: GenAIAgentSpan,
    messages: List<Message>,
    model: LLModel,
    error: Throwable? = null,
    verbose: Boolean = false
) {
    check(span.type == SpanType.INFERENCE) {
        "${span.logString} Expected to end span type of type: <${SpanType.INFERENCE}>, but received span of type: <${span.type}>"
    }

    // error.type
    error?.javaClass?.typeName?.let { typeName ->
        span.addAttribute(CommonAttributes.Error.Type(typeName))
    }

    // gen_ai.response.finish_reasons - Ignore. Not supported in Koog
    // gen_ai.response.id - Ignore. Not supported in Koog
    // gen_ai.response.model
    span.addAttribute(GenAIAttributes.Response.Model(model))

    // gen_ai.usage.input_tokens
    span.addAttribute(
        GenAIAttributes.Usage.InputTokens(
            messages.filterIsInstance<Message.Response>().sumOf { message -> message.metaInfo.inputTokensCount ?: 0 }
        )
    )

    // gen_ai.usage.output_tokens
    span.addAttribute(
        GenAIAttributes.Usage.OutputTokens(
            messages.filterIsInstance<Message.Response>().sumOf { message -> message.metaInfo.outputTokensCount ?: 0 }
        )
    )

    // gen_ai.output.messages
    if (messages.isNotEmpty()) {
        span.addAttribute(GenAIAttributes.Output.Messages(messages))
    }

    span.end(error.toSpanEndStatus(), verbose)
}
