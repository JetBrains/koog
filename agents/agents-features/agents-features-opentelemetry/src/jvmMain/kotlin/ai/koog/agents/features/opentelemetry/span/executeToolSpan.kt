package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.core.feature.model.AIAgentError
import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.agents.features.opentelemetry.attribute.GenAIAttributes
import ai.koog.agents.features.opentelemetry.attribute.KoogAttributes
import ai.koog.agents.features.opentelemetry.extension.toSpanEndStatus
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.Tracer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Build and start a new Execute Tool Span with necessary attributes.
 *
 * Add the necessary attributes for the Execute Tool Span, according to the Open Telemetry Semantic Convention:
 * https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-spans/#execute-tool-span
 *
 * Span attributes:
 * - gen_ai.operation.name (required)
 * - gen_ai.tool.call.arguments (recommended)
 * - gen_ai.tool.call.id (recommended)
 * - gen_ai.tool.description (recommended)
 * - gen_ai.tool.name (recommended)
 *
 * Custom attributes:
 * - koog.event.id
 */
internal fun startExecuteToolSpan(
    tracer: Tracer,
    parentSpan: GenAIAgentSpan?,
    id: String,
    toolName: String,
    toolArgs: JsonObject,
    toolDescription: String?,
    toolCallId: String?,
): GenAIAgentSpan {
    val builder = GenAIAgentSpanBuilder(
        spanType = SpanType.EXECUTE_TOOL,
        parentSpan = parentSpan,
        id = id,
        kind = SpanKind.INTERNAL,
        name = "${GenAIAttributes.Operation.OperationNameType.EXECUTE_TOOL.id} $toolName",
    )
        // gen_ai.operation.name
        .addAttribute(GenAIAttributes.Operation.Name(GenAIAttributes.Operation.OperationNameType.EXECUTE_TOOL))

    // gen_ai.tool.call.id
    toolCallId?.let { callId ->
        builder.addAttribute(GenAIAttributes.Tool.Call.Id(id = callId))
    }

    // gen_ai.tool.description
    toolDescription?.let { description ->
        builder.addAttribute(GenAIAttributes.Tool.Description(description = description))
    }

    // gen_ai.tool.name
    builder.addAttribute(GenAIAttributes.Tool.Name(name = toolName))

    // gen_ai.tool.type
    //   Ignore. Not supported in Koog

    // gen_ai.tool.call.arguments
    builder.addAttribute(GenAIAttributes.Tool.Call.Arguments(toolArgs))

    builder.addAttribute(KoogAttributes.Koog.Event.Id(id))

    return builder.buildAndStart(tracer)
}

/**
 * End Execute Tool Span and set final attributes.
 *
 * Add the necessary attributes for the Execute Tool Span, according to the Open Telemetry Semantic Convention:
 * https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-spans/#execute-tool-span
 *
 * Span attribute:
 * - error.type (conditional)
 * - gen_ai.tool.call.result (recommended)
 */
internal fun endExecuteToolSpan(
    span: GenAIAgentSpan,
    toolResult: JsonElement?,
    error: AIAgentError? = null,
    verbose: Boolean = false
) {
    check(span.type == SpanType.EXECUTE_TOOL) {
        "${span.logString} Expected to end span type of type: <${SpanType.EXECUTE_TOOL}>, but received span of type: <${span.type}>"
    }

    // error.type
    error?.javaClass?.typeName?.let { typeName ->
        span.addAttribute(CommonAttributes.Error.Type(typeName))
    }

    // gen_ai.tool.call.result
    toolResult?.let { result ->
        span.addAttribute(GenAIAttributes.Tool.Call.Result(result))
    }

    span.end(error.toSpanEndStatus(), verbose)
}
