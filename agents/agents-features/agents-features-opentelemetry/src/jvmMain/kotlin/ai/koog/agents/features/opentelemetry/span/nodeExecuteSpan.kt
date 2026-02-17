package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.agents.features.opentelemetry.attribute.GenAIAttributes
import ai.koog.agents.features.opentelemetry.attribute.KoogAttributes
import ai.koog.agents.features.opentelemetry.extension.toSpanEndStatus
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.Tracer

/**
 * Build and start a new Node Execute Span with necessary attributes.
 *
 * Note: This span is out of scope of the Open Telemetry Semantic Convention for GenAI.
 *       It is a custom span used to support Koog events hierarchy.
 *
 * Span attributes:
 * - gen_ai.conversation.id
 *
 * Custom attributes:
 * - koog.node.id
 * - koog.node.input (conditional)
 * - koog.event.id
 */
internal fun startNodeExecuteSpan(
    tracer: Tracer,
    parentSpan: GenAIAgentSpan?,
    id: String,
    runId: String,
    nodeId: String,
    nodeInput: String?,
): GenAIAgentSpan {
    val builder = GenAIAgentSpanBuilder(
        spanType = SpanType.NODE,
        parentSpan = parentSpan,
        id = id,
        kind = SpanKind.INTERNAL,
        name = "node $nodeId",
    )
        .addAttribute(GenAIAttributes.Conversation.Id(runId))
        .addAttribute(KoogAttributes.Koog.Node.Id(nodeId))

    nodeInput?.let { input ->
        builder.addAttribute(KoogAttributes.Koog.Node.Input(input))
    }

    builder.addAttribute(KoogAttributes.Koog.Event.Id(id))

    return builder.buildAndStart(tracer)
}

/**
 * End Node Execute Span and set final attributes.
 *
 * Note: This span is out of scope of the Open Telemetry Semantic Convention for GenAI.
 *       It is a custom span used to support Koog events hierarchy.
 *
 * Span attributes:
 * - error.type (conditional)
 *
 * Custom attributes:
 * - koog.node.output (conditional)
 */
internal fun endNodeExecuteSpan(
    span: GenAIAgentSpan,
    nodeOutput: String?,
    error: Throwable? = null,
    verbose: Boolean = false
) {
    check(span.type == SpanType.NODE) {
        "${span.logString} Expected to end span type of type: <${SpanType.NODE}>, but received span of type: <${span.type}>"
    }

    // error.type
    error?.javaClass?.typeName?.let { typeName ->
        span.addAttribute(CommonAttributes.Error.Type(typeName))
    }

    nodeOutput?.let { output ->
        span.addAttribute(KoogAttributes.Koog.Node.Output(output))
    }

    span.end(error.toSpanEndStatus(), verbose)
}
