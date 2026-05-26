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
 * Build and start a new Node Execute Span with necessary attributes.
 *
 * Note: This span is out of scope of the OpenTelemetry Semantic Convention for GenAI.
 *       It is a custom span used to support Koog events hierarchy.
 *
 * Span attributes:
 * - gen_ai.conversation.id
 *
 * Custom attributes:
 * - koog.node.id
 * - koog.node.input (conditional)
 * - koog.event.id
 *
 * @param tracer The tracer instance to use for creating the span.
 * @param contextFactory The context factory to use for creating the span context.
 * @param parentSpan The parent span for this node execute span.
 * @param id The unique identifier for this span.
 * @param runId The conversation identifier for the parent run.
 * @param nodeId The unique identifier for this node.
 * @param nodeInput The input provided to the node (optional).
 * @param spanAdapter The span adapter to use for custom span behavior (optional).
 * @return The newly created and started GenAIAgentSpan.
 */
internal fun startNodeExecuteSpan(
    tracer: Tracer,
    contextFactory: ContextFactory,
    parentSpan: GenAIAgentSpan?,
    id: String,
    runId: String,
    nodeId: String,
    nodeInput: String?,
    spanAdapter: SpanAdapter? = null,
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

    val span = builder.buildAndStart(tracer, contextFactory)
    spanAdapter?.onBeforeSpanStarted(span)
    return span
}

/**
 * End Node Execute Span and set final attributes. The provided [spanAdapter] is invoked via
 * [SpanAdapter.onBeforeSpanFinished] after all attributes are set and immediately before the
 * underlying span is ended.
 *
 * Note: This span is out of scope of the OpenTelemetry Semantic Convention for GenAI.
 *       It is a custom span used to support Koog events hierarchy.
 *
 * Span attributes:
 * - error.type (conditional)
 *
 * Custom attributes:
 * - koog.node.output (conditional)
 *
 * @param span The GenAIAgentSpan to end.
 * @param nodeOutput The output produced by the node (optional).
 * @param error The error that occurred during the node execution (optional).
 * @param verbose Whether to log verbose information during span ending (default: false).
 * @param spanAdapter The span adapter to use for custom span behavior (optional).
 */
internal fun endNodeExecuteSpan(
    span: GenAIAgentSpan,
    nodeOutput: String?,
    error: Throwable? = null,
    verbose: Boolean = false,
    spanAdapter: SpanAdapter? = null,
) {
    check(span.type == SpanType.NODE) {
        "${span.logString} Expected to end span type of type: <${SpanType.NODE}>, but received span of type: <${span.type}>"
    }

    // error.type
    span.addCommonErrorAttributes(error)

    nodeOutput?.let { output ->
        span.addAttribute(KoogAttributes.Koog.Node.Output(output))
    }

    spanAdapter?.onBeforeSpanFinished(span)
    span.end(error.toStatusData(), verbose)
}
