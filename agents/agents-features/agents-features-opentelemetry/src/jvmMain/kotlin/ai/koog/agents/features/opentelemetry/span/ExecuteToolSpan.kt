package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes
import io.opentelemetry.api.trace.SpanKind

/**
 * Tool Call Span
 */
internal class ExecuteToolSpan(
    parent: NodeExecuteSpan,
    val toolName: String,
    val toolArgs: String?,
    val toolCallId: String?,
) : GenAIAgentSpan(parent) {

    companion object {
        fun createId(agentId: String, runId: String, nodeName: String, nodeId: String, toolName: String, toolArgs: String): String =
            createIdFromParent(
                parentId = NodeExecuteSpan.createId(agentId, runId, nodeName, nodeId),
                toolName = toolName,
                toolArgs = toolArgs
            )

        private fun createIdFromParent(parentId: String, toolName: String, toolArgs: String?): String =
            // TODO: Replace sha256base64() with unique event id for the Tool Call event
            "$parentId.tool.$toolName.args.${toolArgs?.sha256base64()}"
    }

    override val spanId: String =
        createIdFromParent(parentId = parent.spanId, toolName = toolName, toolArgs = toolArgs)

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
        // gen_ai.operation.name
        addAttribute(SpanAttributes.Operation.Name(SpanAttributes.Operation.OperationNameType.EXECUTE_TOOL))

        // gen_ai.tool.name
        addAttribute(SpanAttributes.Tool.Name(name = toolName))

        // gen_ai.tool.call.id
        toolCallId?.let { id ->
            addAttribute(SpanAttributes.Tool.Call.Id(id = id))
        }

        // Tool arguments custom attribute
        toolArgs?.let { toolArgs ->
            addAttribute(SpanAttributes.Tool.InputValue(toolArgs))
        }
    }
}
