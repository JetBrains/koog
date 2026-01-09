package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes
import io.opentelemetry.api.trace.SpanKind

/**
 * Tool Call Span
 */
internal class ExecuteToolSpan(
    override val id: String,
    override val parentSpan: NodeExecuteSpan,
    val toolName: String,
    val toolArgs: String,
    val toolDescription: String?,
    val toolCallId: String?,
) : GenAIAgentSpan() {

    override val kind: SpanKind = SpanKind.INTERNAL

    override val name: String = "${SpanAttributes.Operation.OperationNameType.EXECUTE_TOOL.id} $toolName"

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

        // Tool arguments custom attribute
        addAttribute(SpanAttributes.Tool.InputValue(toolArgs))

        // gen_ai.tool.description
        toolDescription?.let { description ->
            addAttribute(SpanAttributes.Tool.Description(description = description))
        }

        // gen_ai.tool.call.id
        toolCallId?.let { id ->
            addAttribute(SpanAttributes.Tool.Call.Id(id = id))
        }
    }
}
