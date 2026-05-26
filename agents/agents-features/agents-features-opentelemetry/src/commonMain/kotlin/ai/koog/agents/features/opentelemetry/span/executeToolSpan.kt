package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.agents.features.opentelemetry.attribute.GenAIAttributes
import ai.koog.agents.features.opentelemetry.attribute.KoogAttributes
import ai.koog.agents.features.opentelemetry.attribute.McpAttributes
import ai.koog.agents.features.opentelemetry.extension.addCommonErrorAttributes
import ai.koog.agents.features.opentelemetry.extension.toStatusData
import ai.koog.agents.features.opentelemetry.integration.SpanAdapter
import ai.koog.agents.mcp.metadata.McpMetadataKeys
import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.kotlin.factory.ContextFactory
import io.opentelemetry.kotlin.tracing.SpanKind
import io.opentelemetry.kotlin.tracing.Tracer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

private val logger = KotlinLogging.logger { }

/**
 * Build and start a new Execute Tool Span with necessary attributes. When [mcpToolMetadata] is
 * provided (i.e., the tool is sourced via MCP), the span is enriched with MCP-specific attributes
 * before [SpanAdapter.onBeforeSpanStarted] is invoked, so adapters observe the fully prepared
 * attribute set.
 *
 * Add the necessary attributes for the Execute Tool Span, according to the OpenTelemetry Semantic Convention:
 * https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-spans/#execute-tool-span
 *
 * Span attributes:
 * - gen_ai.operation.name (required)
 * - gen_ai.provider.name (required) - [KoogAttributes.PROVIDER_NAME]
 * - gen_ai.tool.call.arguments (recommended)
 * - gen_ai.tool.call.id (recommended)
 * - gen_ai.tool.description (recommended)
 * - gen_ai.tool.name (recommended)
 *
 * Custom attributes:
 * - koog.event.id
 *
 * @param tracer The tracer instance to use for creating the span.
 * @param contextFactory The context factory to use for creating the span context.
 * @param parentSpan The parent span for the new span.
 * @param id The unique identifier for the span.
 * @param toolName The name of the tool being called.
 * @param toolArgs The arguments for the tool call.
 * @param toolDescription The description of the tool.
 * @param toolCallId The identifier for the tool call.
 * @param mcpToolMetadata Optional metadata for the tool sourced via MCP.
 * @param spanAdapter Optional span adapter for customizing the span behavior.
 */
internal fun startExecuteToolSpan(
    tracer: Tracer,
    contextFactory: ContextFactory,
    parentSpan: GenAIAgentSpan?,
    id: String,
    toolName: String,
    toolArgs: JsonObject,
    toolDescription: String?,
    toolCallId: String?,
    mcpToolMetadata: Map<String, String>? = null,
    spanAdapter: SpanAdapter? = null,
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
        // gen_ai.provider.name
        .addAttribute(GenAIAttributes.Provider.Name(KoogAttributes.PROVIDER_NAME))

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

    val span = builder.buildAndStart(tracer, contextFactory)

    if (mcpToolMetadata != null) {
        val mcpVersion = mcpToolMetadata[McpMetadataKeys.McpProtocolVersion]
        val mcpTransportType = mcpToolMetadata[McpMetadataKeys.McpTransportType]
        if (mcpVersion != null && mcpTransportType != null) {
            span.enrichExecuteToolSpanWithMcpAttributes(
                toolName = toolName,
                sessionId = mcpToolMetadata[McpMetadataKeys.McpSessionId],
                methodName = "tools/call",
                serverPort = mcpToolMetadata[McpMetadataKeys.ServerPort]?.toIntOrNull(),
                serverAddress = mcpToolMetadata[McpMetadataKeys.ServerUrl],
                mcpProtocolVersion = mcpVersion,
                mcpTransportType = mcpTransportType,
            )
        } else {
            logger.error {
                "MCP protocol version ($mcpVersion) and transport type ($mcpTransportType) are required " +
                    "for mcp tool call spans: tool=$toolName, " +
                    "serverUrl=${mcpToolMetadata[McpMetadataKeys.ServerUrl]}"
            }
        }
    }

    spanAdapter?.onBeforeSpanStarted(span)
    return span
}

/**
 * End Execute Tool Span and set final attributes. The provided [spanAdapter] is invoked via
 * [SpanAdapter.onBeforeSpanFinished] after all attributes are set and immediately before the
 * underlying span is ended.
 *
 * Add the necessary attributes for the Execute Tool Span, according to the OpenTelemetry Semantic Convention:
 * https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-spans/#execute-tool-span
 *
 * Span attribute:
 * - error.type (conditional)
 * - gen_ai.tool.call.result (recommended)
 *
 * @param span The span to end.
 * @param toolResult The result of the tool call.
 * @param error The error that occurred during the tool call, if any.
 * @param verbose Whether to log verbose information.
 * @param spanAdapter Optional span adapter for customizing the span behavior.
 */
internal fun endExecuteToolSpan(
    span: GenAIAgentSpan,
    toolResult: JsonElement?,
    error: Throwable? = null,
    verbose: Boolean = false,
    spanAdapter: SpanAdapter? = null,
) {
    check(span.type == SpanType.EXECUTE_TOOL) {
        "${span.logString} Expected to end span type of type: <${SpanType.EXECUTE_TOOL}>, but received span of type: <${span.type}>"
    }

    // error.type
    span.addCommonErrorAttributes(error)

    // gen_ai.tool.call.result
    toolResult?.let { result ->
        span.addAttribute(GenAIAttributes.Tool.Call.Result(result))
    }

    spanAdapter?.onBeforeSpanFinished(span)
    span.end(error.toStatusData(), verbose)
}

/**
 * Adds MCP-specific attributes to this Execute Tool span. Caller must ensure the receiver wraps an MCP tool invocation.
 * The type is not checked here. Attribute requirement levels follow the OpenTelemetry GenAI/MCP
 * semantic conventions and are noted next to each `addAttribute` call.
 *
 * @param toolName The name of the MCP tool being called;
 * @param methodName MCP method invoked;
 * @param serverAddress MCP server address, when known;
 * @param serverPort MCP server port, when known;
 * @param sessionId MCP session identifier, when the call is part of a session;
 * @param mcpProtocolVersion MCP protocol version in use;
 * @param mcpTransportType Transport used to reach the MCP server ("stdio", "http");
 * @return The receiver span, for chaining.
 */
private fun GenAIAgentSpan.enrichExecuteToolSpanWithMcpAttributes(
    toolName: String,
    methodName: String,
    serverAddress: String? = null,
    serverPort: Int? = null,
    sessionId: String? = null,
    mcpProtocolVersion: String,
    mcpTransportType: String,
): GenAIAgentSpan {
    // mcp.method.name (REQUIRED)
    addAttribute(McpAttributes.Mcp.Method.Name(methodName))

    // gen_ai.operation.name (RECOMMENDED for tool calls)
    addAttribute(GenAIAttributes.Operation.Name(GenAIAttributes.Operation.OperationNameType.EXECUTE_TOOL))

    // gen_ai.tool.name (CONDITIONALLY REQUIRED)
    addAttribute(GenAIAttributes.Tool.Name(toolName))

    // mcp.session.id (RECOMMENDED)
    sessionId?.let { session ->
        addAttribute(McpAttributes.Mcp.Session.Id(session))
    }

    // mcp.protocol.version (RECOMMENDED)
    addAttribute(McpAttributes.Mcp.Protocol.Version(mcpProtocolVersion))
    // network.transport (RECOMMENDED)
    addAttribute(McpAttributes.Network.Transport(mcpTransportType))

    // server.address (RECOMMENDED)
    serverAddress?.let { address ->
        addAttribute(CommonAttributes.Server.Address(address))
    }

    // server.port (RECOMMENDED)
    serverPort?.let { port ->
        addAttribute(CommonAttributes.Server.Port(port))
    }

    return this
}
