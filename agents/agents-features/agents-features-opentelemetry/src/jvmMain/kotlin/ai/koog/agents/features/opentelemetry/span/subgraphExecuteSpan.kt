package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.agents.features.opentelemetry.attribute.KoogAttributes
import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes
import ai.koog.agents.features.opentelemetry.extension.toSpanEndStatus
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.Tracer

/**
 * Build and start a new Subgraph Execute Span with necessary attributes.
 *
 * Note: This span is out of scope of the Open Telemetry Semantic Convention for GenAI.
 *       It is a custom span used to support Koog events hierarchy.
 *
 * Span attributes:
 * - gen_ai.conversation.id
 *
 * Custom attributes:
 * - koog.subgraph.id
 * - koog.subgraph.input (conditional)
 * - koog.event.id
 */
internal fun startSubgraphExecuteSpan(
    tracer: Tracer,
    parentSpan: GenAIAgentSpan?,
    id: String,
    runId: String,
    subgraphId: String,
    subgraphInput: String?,
): GenAIAgentSpan {
    val builder = GenAIAgentSpanBuilder(
        spanType = SpanType.SUBGRAPH,
        parentSpan = parentSpan,
        id = id,
        kind = SpanKind.INTERNAL,
        name = "subgraph $subgraphId",
    )
        .addAttribute(SpanAttributes.Conversation.Id(runId))
        .addAttribute(KoogAttributes.Koog.Subgraph.Id(subgraphId))

    subgraphInput?.let { input ->
        builder.addAttribute(KoogAttributes.Koog.Subgraph.Input(input))
    }

    builder.addAttribute(KoogAttributes.Koog.Event.Id(id))

    return builder.buildAndStart(tracer)
}

/**
 * End Subgraph Execute Span and set final attributes.
 *
 * Note: This span is out of scope of the Open Telemetry Semantic Convention for GenAI.
 *       It is a custom span used to support Koog events hierarchy.
 *
 * Span attributes:
 * - error.type (conditional)
 *
 * Custom attributes:
 * - koog.subgraph.output (conditional)
 */
internal fun endSubgraphExecuteSpan(
    span: GenAIAgentSpan,
    subgraphOutput: String?,
    error: Throwable? = null,
    verbose: Boolean = false
) {
    check(span.type == SpanType.SUBGRAPH) {
        "${span.logString} Expected to end span type of type: <${SpanType.SUBGRAPH}>, but received span of type: <${span.type}>"
    }

    // error.type
    error?.javaClass?.typeName?.let { typeName ->
        span.addAttribute(CommonAttributes.Error.Type(typeName))
    }

    subgraphOutput?.let { output ->
        span.addAttribute(KoogAttributes.Koog.Subgraph.Output(output))
    }

    span.end(error.toSpanEndStatus(), verbose)
}
