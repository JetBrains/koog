package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes
import io.opentelemetry.api.trace.SpanKind

internal class StrategySpan(
    override val id: String,
    override val parentSpan: GenAIAgentSpan,
    val runId: String,
    val strategyName: String,
) : GenAIAgentSpan() {

    override val kind: SpanKind = SpanKind.INTERNAL

    override val name: String = strategyName

    /**
     * Add the necessary attributes for the Strategy Span:
     *
     * Note: Strategy Span is not defined in the Open Telemetry Semantic Convention.
     *       It is a custom span used to support Koog events hierarchy
     */
    init {
        addAttribute(SpanAttributes.Conversation.Id(runId))
        addAttribute(CustomAttribute("koog.strategy.name", strategyName))
    }
}
