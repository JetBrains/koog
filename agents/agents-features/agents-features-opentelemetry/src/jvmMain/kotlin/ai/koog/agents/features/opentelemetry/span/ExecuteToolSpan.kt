package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.features.opentelemetry.attribute.Attribute
import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes
import io.opentelemetry.api.trace.SpanKind

/**
 * Tool Call Span
 */
internal class ExecuteToolSpan(
    parent: NodeExecuteSpan,
    private val tool: Tool<*, *>,
) : GenAIAgentSpan(parent) {

    companion object {
        fun createId(agentId: String, runId: String, nodeName: String, toolName: String): String =
            createIdFromParent(parentId = NodeExecuteSpan.createId(agentId, runId, nodeName), toolName = toolName)

        private fun createIdFromParent(parentId: String, toolName: String): String =
            "$parentId.tool.$toolName"
    }

    override val spanId: String = createIdFromParent(parent.spanId, tool.name)

    override val kind: SpanKind = SpanKind.INTERNAL

    /**
     * Add the necessary attributes for the Execute Tool Span according to the Open Telemetry Semantic Convention:
     * https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-spans/#execute-tool-span
     *
     * Attribute description:
     * - error.type (conditional)
     * - gen_ai.tool.call.id (recommended)
     * - gen_ai.tool.description (recommended)
     * - gen_ai.tool.name (recommended)
     */
    override val attributes: List<Attribute> = buildList {
        // gen_ai.tool.description
        add(SpanAttributes.Tool.Description(description = tool.descriptor.description))

        // gen_ai.tool.name
        add(SpanAttributes.Tool.Name(name = tool.name))
    }
}