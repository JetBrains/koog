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
    parent: InvokeAgentSpan,
    val runId: String,
    val nodeName: String,
    val nodeInput: String?
) : GenAIAgentSpan(parent) {

    companion object {
        fun createId(agentId: String, runId: String, nodeName: String): String =
            createIdFromParent(parentId = InvokeAgentSpan.createId(agentId, runId), nodeName = nodeName)

        private fun createIdFromParent(parentId: String, nodeName: String): String =
            "$parentId.node.$nodeName"
    }

    override val spanId: String = createIdFromParent(parent.spanId, nodeName)

    override val kind: SpanKind = SpanKind.INTERNAL

    /**
     * Add the necessary attributes for the Node Execute Span:
     *
     * Note: Node Execute Span is not defined in the Open Telemetry Semantic Convention.
     *       It is a custom span used to show a structure of Koog events
     */
    init {
        addAttribute(SpanAttributes.Conversation.Id(runId))
        addAttribute(CustomAttribute("koog.node.name", nodeName))
        nodeInput?.let { input ->
            addAttribute(CustomAttribute("koog.node.input", HiddenString(input)))
        }
    }
}
