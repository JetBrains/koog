package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes
import ai.koog.agents.utils.HiddenString
import io.opentelemetry.api.trace.SpanKind

/**
 * Subgraph Execute Span
 *
 * Note: This span is out of scope of Open Telemetry Semantic Convention for GenAI.
 */
internal class SubgraphExecuteSpan(
    override val id: String,
    override val parentSpan: GenAIAgentSpan,
    val runId: String,
    val subgraphId: String,
    val subgraphInput: String?,
) : GenAIAgentSpan() {

    override val kind: SpanKind = SpanKind.INTERNAL

    override val name: String = "$subgraphId.$id"

    /**
     * Add the necessary attributes for the Subgraph Execute Span:
     *
     * Note: Subgraph Execute Span is not defined in the Open Telemetry Semantic Convention.
     *       It is a custom span used to support Koog events hierarchy
     */
    init {
        addAttribute(SpanAttributes.Conversation.Id(runId))
        addAttribute(CustomAttribute("koog.subgraph.id", subgraphId))
        subgraphInput?.let { input ->
            addAttribute(CustomAttribute("koog.subgraph.input", HiddenString(input)))
        }
    }
}
