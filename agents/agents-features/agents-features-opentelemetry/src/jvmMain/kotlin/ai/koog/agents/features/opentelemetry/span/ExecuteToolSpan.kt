package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes
import io.opentelemetry.api.trace.SpanKind

/**
 * Tool Call Span
 */
internal class ExecuteToolSpan(
    parent: NodeExecuteSpan,
    tool: Tool<*, *>,
    private val toolArgs: Any?,
    toolCallId: String?,
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
     * Add the necessary attributes for the Execute Tool Span, according to the Open Telemetry Semantic Convention:
     * https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-spans/#execute-tool-span
     *
     * Attribute description:
     * - error.type (conditional)
     * - gen_ai.tool.call.id (recommended)
     * - gen_ai.tool.description (recommended)
     * - gen_ai.tool.name (recommended)
     */
    init {
        // gen_ai.tool.description
        addAttribute(SpanAttributes.Tool.Description(description = tool.descriptor.description))

        // gen_ai.tool.name
        addAttribute(SpanAttributes.Tool.Name(name = tool.name))

        // gen_ai.tool.call.id
        toolCallId?.let { id ->
            addAttribute(SpanAttributes.Tool.Call.Id(id = id))
        }

        // Tool arguments custom attribute
        @Suppress("UNCHECKED_CAST")
        (tool as? Tool<Any?, Any?>)?.let { tool ->
            addAttribute(SpanAttributes.Tool.InputValue(tool.encodeArgsToString(toolArgs)))
        }
    }
}
