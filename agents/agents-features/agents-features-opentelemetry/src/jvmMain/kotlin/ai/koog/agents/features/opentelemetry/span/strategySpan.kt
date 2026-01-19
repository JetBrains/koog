package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.agents.features.opentelemetry.attribute.KoogAttributes
import ai.koog.agents.features.opentelemetry.attribute.GenAIAttributes
import ai.koog.agents.features.opentelemetry.extension.toSpanEndStatus
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.Tracer

/**
 * Build and start a new Strategy Span with necessary attributes.
 *
 * Note: This span is not a standard span type defined in the Open Telemetry
 * Semantic Conventions but is designed to provide support for tracing
 * operations related to strategy execution in Koog events.
 *
 * Span attributes:
 * - gen_ai.conversation.id
 *
 * Custom attributes:
 * - koog.strategy.name
 * - koog.event.id
 */
internal fun startStrategySpan(
    tracer: Tracer,
    parentSpan: GenAIAgentSpan?,
    id: String,
    runId: String,
    strategyName: String,
): GenAIAgentSpan {
    val builder = GenAIAgentSpanBuilder(
        spanType = SpanType.STRATEGY,
        parentSpan = parentSpan,
        id = id,
        kind = SpanKind.INTERNAL,
        name = "strategy $strategyName",
    )
        .addAttribute(GenAIAttributes.Conversation.Id(runId))
        .addAttribute(KoogAttributes.Koog.Strategy.Name(strategyName))
        .addAttribute(KoogAttributes.Koog.Event.Id(id))

    return builder.buildAndStart(tracer)
}

/**
 * End Strategy Span and set final attributes.
 *
 * Note: This span is not a standard span type defined in the Open Telemetry
 * Semantic Conventions but is designed to provide support for tracing
 * operations related to strategy execution in Koog events.
 *
 * Span attributes:
 * - error.type (conditional)
 */
internal fun endStrategySpan(
    span: GenAIAgentSpan,
    error: Throwable? = null,
    verbose: Boolean = false,
) {
    check(span.type == SpanType.STRATEGY) {
        "${span.logString} Expected to end span type of type: <${SpanType.STRATEGY}>, but received span of type: <${span.type}>"
    }

    // error.type
    error?.javaClass?.typeName?.let { typeName ->
        span.addAttribute(CommonAttributes.Error.Type(typeName))
    }

    span.end(error.toSpanEndStatus(), verbose)
}
