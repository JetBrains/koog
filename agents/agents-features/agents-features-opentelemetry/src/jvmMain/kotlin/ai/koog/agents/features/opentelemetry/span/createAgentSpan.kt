package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.agents.features.opentelemetry.attribute.GenAIAttributes
import ai.koog.agents.features.opentelemetry.attribute.KoogAttributes
import ai.koog.agents.features.opentelemetry.extension.toSpanEndStatus
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.Tracer

/**
 * Build and start a new Create Agent Span with necessary attributes.
 *
 * Add the necessary attributes for the Create Agent Span according to the Open Telemetry Semantic Convention:
 * https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-agent-spans/#create-agent-span
 *
 * Span attributes:
 * - gen_ai.operation.name (required)
 * - gen_ai.provider.name (required)
 * - gen_ai.agent.description (conditional)
 * - gen_ai.agent.id (conditional)
 * - gen_ai.agent.name (conditional)
 * - gen_ai.request.model (conditional)
 * - gen_ai.system_instructions (recommended)
 * - server.port (conditional/required)
 * - server.address (recommended)
 *
 * Custom attributes:
 * - koog.event.id
 */
internal fun startCreateAgentSpan(
    tracer: Tracer,
    parentSpan: GenAIAgentSpan?,
    id: String,
    agentId: String,
    model: LLModel,
    messages: List<Message>
): GenAIAgentSpan {
    val builder = GenAIAgentSpanBuilder(
        spanType = SpanType.CREATE_AGENT,
        parentSpan = parentSpan,
        id = id,
        kind = SpanKind.CLIENT,
        name = "${GenAIAttributes.Operation.OperationNameType.CREATE_AGENT.id} $agentId",
    )
        // gen_ai.operation.name
        .addAttribute(GenAIAttributes.Operation.Name(GenAIAttributes.Operation.OperationNameType.CREATE_AGENT))
        // gen_ai.provider.name
        .addAttribute(GenAIAttributes.Provider.Name(model.provider))
        // gen_ai.request.model
        .addAttribute(GenAIAttributes.Request.Model(model))
        // gen_ai.agent.description - Ignore. Not supported in Koog
        // gen_ai.agent.id
        .addAttribute(GenAIAttributes.Agent.Id(agentId))

    // gen_ai.agent.name - Ignore. Not supported in Koog
    // server.port - Ignore. Not supported in Koog
    // server.address - Ignore. Not supported in Koog
    // gen_ai.system_instructions
    val systemMessages = messages.filterIsInstance<Message.System>()
    if (systemMessages.isNotEmpty()) {
        builder.addAttribute(GenAIAttributes.SystemInstructions(systemMessages))
    }

    builder.addAttribute(KoogAttributes.Koog.Event.Id(id))

    return builder.buildAndStart(tracer)
}

/**
 * End Create Agent Span and set final attributes.
 *
 * Add the necessary attributes for the Create Agent Span according to the Open Telemetry Semantic Convention:
 * https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-agent-spans/#create-agent-span
 *
 * Span attribute:
 * - error.type (conditional)
 */
internal fun endCreateAgentSpan(
    span: GenAIAgentSpan,
    error: Throwable? = null,
    verbose: Boolean = false
) {
    check(span.type == SpanType.CREATE_AGENT) {
        "${span.logString} Expected to end span type of type: <${SpanType.CREATE_AGENT}>, but received span of type: <${span.type}>"
    }

    // error.type
    error?.javaClass?.typeName?.let { typeName ->
        span.addAttribute(CommonAttributes.Error.Type(typeName))
    }

    span.end(error.toSpanEndStatus(), verbose)
}
