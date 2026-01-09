package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes
import ai.koog.agents.utils.HiddenString
import io.opentelemetry.api.trace.SpanKind

/**
 * Node Execute Span
 *
 * Note: This span is out of scope of Open Telemetry Semantic Convention for GenAI.
 */
internal class NodeExecuteSpan(
    override val id: String,
    override val parentSpan: GenAIAgentSpan,
    val runId: String,
    val nodeId: String,
    val nodeInput: String?,
) : GenAIAgentSpan() {

    override val kind: SpanKind = SpanKind.INTERNAL

    override val name: String = "node $nodeId"

    /**
     * Add the necessary attributes for the Node Execute Span:
     *
     * Note: Node Execute Span is not defined in the Open Telemetry Semantic Convention.
     *       It is a custom span used to support Koog events hierarchy
     */
    init {
        addAttribute(SpanAttributes.Conversation.Id(runId))
        addAttribute(CustomAttribute("koog.node.id", nodeId))
        nodeInput?.let { input ->
            addAttribute(CustomAttribute("koog.node.input", HiddenString(input)))
        }
    }
}
