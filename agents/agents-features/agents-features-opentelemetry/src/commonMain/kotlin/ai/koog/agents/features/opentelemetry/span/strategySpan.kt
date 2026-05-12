package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.features.opentelemetry.attribute.GenAIAttributes
import ai.koog.agents.features.opentelemetry.attribute.KoogAttributes
import ai.koog.agents.features.opentelemetry.extension.addCommonErrorAttributes
import ai.koog.agents.features.opentelemetry.extension.toStatusData
import ai.koog.agents.features.opentelemetry.integration.SpanAdapter
import io.opentelemetry.kotlin.factory.ContextFactory
import io.opentelemetry.kotlin.tracing.SpanKind
import io.opentelemetry.kotlin.tracing.Tracer

/**
 * Build and start a new Strategy Span with necessary attributes.
 *
 * Note: This span is not a standard span type defined in the OpenTelemetry
 * Semantic Conventions but is designed to provide support for tracing
 * operations related to strategy execution in Koog events.
 *
 * Span attributes:
 * - gen_ai.conversation.id
 *
 * Custom attributes:
 * - koog.strategy.name
 * - koog.event.id
 *
 * @param tracer The tracer instance to use for creating the span.
 * @param contextFactory The context factory to use for creating the span context.
 * @param parentSpan The parent span for this strategy span.
 * @param id The unique identifier for this span.
 * @param runId The conversation identifier for the parent run.
 * @param strategyName The name of the strategy being executed.
 * @param spanAdapter The span adapter to use for custom span behavior (optional).
 * @return The newly created and started GenAIAgentSpan.
 */
internal fun startStrategySpan(
    tracer: Tracer,
    contextFactory: ContextFactory,
    parentSpan: GenAIAgentSpan?,
    id: String,
    runId: String,
    strategyName: String,
    spanAdapter: SpanAdapter? = null,
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

    val span = builder.buildAndStart(tracer, contextFactory)
    spanAdapter?.onBeforeSpanStarted(span)
    return span
}

/**
 * End Strategy Span and set final attributes. The provided [spanAdapter] is invoked via
 * [SpanAdapter.onBeforeSpanFinished] after all attributes are set and immediately before the
 * underlying span is ended.
 *
 * Note: This span is not a standard span type defined in the OpenTelemetry
 * Semantic Conventions but is designed to provide support for tracing
 * operations related to strategy execution in Koog events.
 *
 * Span attributes:
 * - error.type (conditional)
 *
 * @param span The GenAIAgentSpan to end.
 * @param error The error that occurred during the strategy execution (optional).
 * @param verbose Whether to log verbose information during span ending (default: false).
 * @param spanAdapter The span adapter to use for custom span behavior (optional).
 */
internal fun endStrategySpan(
    span: GenAIAgentSpan,
    error: Throwable? = null,
    verbose: Boolean = false,
    spanAdapter: SpanAdapter? = null,
) {
    check(span.type == SpanType.STRATEGY) {
        "${span.logString} Expected to end span type of type: <${SpanType.STRATEGY}>, but received span of type: <${span.type}>"
    }

    // error.type
    span.addCommonErrorAttributes(error)

    spanAdapter?.onBeforeSpanFinished(span)
    span.end(error.toStatusData(), verbose)
}
