package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.core.feature.handler.tool.McpTransportType
import ai.koog.agents.core.feature.model.AIAgentError
import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.agents.features.opentelemetry.attribute.McpAttributes
import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes
import ai.koog.agents.features.opentelemetry.extension.toSpanEndStatus
import ai.koog.agents.features.opentelemetry.integration.mcp.McpMethod
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.Tracer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Builds and starts a new MCP Client span with MCP-specific attributes.
 *
 * This function creates an OpenTelemetry span for MCP client operations following
 * the OpenTelemetry Semantic Conventions for MCP:
 * https://github.com/open-telemetry/semantic-conventions/pull/2083
 *
 * ## Span Naming Convention
 * Spans are named using the pattern: `"{mcp.method.name} {target}"`
 * where `{target}` is the tool name, prompt name, or resource URI when applicable.
 *
 * Examples:
 * - `"tools/call search"` - calling a tool named "search"
 * - `"prompts/get template"` - getting a prompt named "template"
 * - `"resources/read"` - reading a resource (no specific target)
 *
 * ## Attributes by Requirement Level
 *
 * **Required attributes:**
 * - `mcp.method.name`: The MCP method being invoked
 *
 * **Conditionally required attributes:**
 * - `gen_ai.tool.name`: Tool name (when operation involves a tool)
 * - `jsonrpc.request.id`: JSON-RPC request ID (when client executes a request, not a notification)
 * - `mcp.resource.uri`: Resource URI (when request includes a resource)
 * - `error.type`: Error type (added at span end if operation fails)
 * - `rpc.response.status_code`: RPC status code (added at span end if response contains error)
 *
 * **Recommended attributes:**
 * - `gen_ai.operation.name`: Set to "execute_tool" for tool calls
 * - `mcp.protocol.version`: MCP protocol version (e.g., "2025-06-18")
 * - `mcp.session.id`: Session identifier (when request is part of a session)
 * - `network.transport`: Transport type ("pipe", "tcp", or "quic")
 * - `server.address`: Server address for client operations
 * - `server.port`: Server port for client operations
 *
 * **Optional attributes:**
 * - `gen_ai.tool.call.id`: Tool call identifier
 * - `gen_ai.tool.description`: Tool description
 * - `gen_ai.tool.call.arguments`: Tool arguments (sensitive data)
 * - `gen_ai.tool.call.result`: Tool result (sensitive data, added at span end)
 *
 * @param tracer The OpenTelemetry tracer to create the span.
 * @param parentSpan The parent span for this MCP operation (if any).
 * @param id Unique identifier for this span.
 * @param method The MCP method being invoked.
 * @param requestId The JSON-RPC request ID (required for requests, not notifications).
 * @param toolName The name of the tool being called (required for tool operations).
 * @param resourceUri The URI of the resource being accessed (required for resource operations).
 * @param toolArgs The arguments passed to the tool (optional, sensitive).
 * @param toolCallId Unique identifier for this tool call (optional).
 * @param toolDescription Description of the tool (optional).
 * @param serverAddress The server address for client spans (recommended).
 * @param serverPort The server port for client spans (recommended).
 * @param sessionId The MCP session identifier (recommended when part of a session).
 * @param mcpProtocolVersion The MCP protocol version in use (recommended).
 * @param mcpTransportType The transport type used for communication (recommended).
 * @return A started GenAIAgentSpan configured for MCP client operations.
 */
internal fun startMcpClientSpan(
    tracer: Tracer,
    parentSpan: GenAIAgentSpan?,
    id: String,
    method: McpMethod,
    requestId: String? = null,
    toolName: String? = null,
    resourceUri: String? = null,
    toolArgs: JsonObject? = null,
    toolCallId: String? = null,
    toolDescription: String? = null,
    serverAddress: String? = null,
    serverPort: Int? = null,
    sessionId: String? = null,
    mcpProtocolVersion: String,
    mcpTransportType: McpTransportType,
): GenAIAgentSpan {
    // Build span name: "{mcp.method.name} {target}"
    val target = toolName ?: resourceUri ?: ""
    val methodName = method.methodName
    val spanName = if (target.isNotEmpty()) {
        "$methodName $target"
    } else {
        methodName
    }

    // mcp.method.name (REQUIRED)
    val builder = GenAIAgentSpanBuilder(
        spanType = SpanType.MCP,
        parentSpan = parentSpan,
        id = id,
        kind = SpanKind.CLIENT,
        name = spanName,
    ).addAttribute(McpAttributes.Mcp.Method.Name(methodName))

    // gen_ai.operation.name (RECOMMENDED for tool calls)
    toolName.let {
        builder.addAttribute(SpanAttributes.Operation.Name(SpanAttributes.Operation.OperationNameType.EXECUTE_TOOL))
    }

    // gen_ai.tool.name (CONDITIONALLY REQUIRED)
    toolName?.let { name ->
        builder.addAttribute(SpanAttributes.Tool.Name(name))
    }

    // jsonrpc.request.id (CONDITIONALLY REQUIRED)
    requestId?.let { reqId ->
        builder.addAttribute(McpAttributes.JsonRpc.Request.Id(reqId))
    }

    // mcp.session.id (RECOMMENDED)
    sessionId?.let { session ->
        builder.addAttribute(McpAttributes.Mcp.Session.Id(session))
    }

    // mcp.protocol.version (RECOMMENDED)
    mcpProtocolVersion.let { version ->
        builder.addAttribute(McpAttributes.Mcp.Protocol.Version(version))
    }

    // network.transport (RECOMMENDED)
    mcpTransportType.let { transport ->
        builder.addAttribute(McpAttributes.Network.Transport(transport.value))
    }

    // server.address (RECOMMENDED)
    serverAddress?.let { address ->
        builder.addAttribute(McpAttributes.Server.Address(address))
    }

    // server.port (RECOMMENDED)
    serverPort?.let { port ->
        builder.addAttribute(McpAttributes.Server.Port(port))
    }

    // gen_ai.tool.call.id (OPTIONAL/RECOMMENDED for tool calls)
    toolCallId?.let { callId ->
        builder.addAttribute(SpanAttributes.Tool.Call.Id(callId))
    }

    // gen_ai.tool.description (RECOMMENDED for tool calls)
    toolDescription?.let { description ->
        builder.addAttribute(SpanAttributes.Tool.Description(description))
    }

    // gen_ai.tool.call.arguments (OPTIONAL, sensitive)
    toolArgs?.let { args ->
        builder.addAttribute(SpanAttributes.Tool.Call.Arguments(args))
    }

    return builder.buildAndStart(tracer)
}

/**
 * Ends an MCP Client span and sets final attributes based on the operation result.
 *
 * This function completes an MCP client span by adding result-specific attributes
 * and setting the appropriate span status based on whether the operation succeeded or failed.
 *
 * ## Attributes Added
 *
 * **Conditionally required:**
 * - `error.type`: The error type (added if operation failed)
 * - `rpc.response.status_code`: The JSON-RPC error code (added if response contains error)
 *
 * **Optional:**
 * - `gen_ai.tool.call.result`: The tool call result (sensitive data, added if verbose mode enabled)
 *
 * @param span The MCP client span to end (must be of type SpanType.MCP).
 * @param error The error that occurred (if any). When present, sets error.type attribute and ERROR status.
 * @param rpcStatusCode The JSON-RPC status code (if present in error response).
 * @param toolCallResult The result of the tool call (optional, sensitive data).
 * @param verbose Whether to include sensitive attributes like tool call results.
 * @throws IllegalStateException if the span is not of type SpanType.MCP.
 */
internal fun endMcpClientSpan(
    span: GenAIAgentSpan,
    error: AIAgentError? = null,
    rpcStatusCode: String? = null,
    toolCallResult: JsonElement? = null,
    verbose: Boolean = false
) {
    check(span.type == SpanType.MCP) {
        "${span.logString} Expected to end span type of type: <${SpanType.MCP}>, but received span of type: <${span.type}>"
    }

    // error.type (CONDITIONALLY REQUIRED)
    error?.let { err ->
        span.addAttribute(CommonAttributes.Error.Type(err.javaClass.typeName))
    }

    // rpc.response.status_code (CONDITIONALLY REQUIRED)
    rpcStatusCode?.let { code ->
        span.addAttribute(McpAttributes.Rpc.Response.StatusCode(code))
    }

    // gen_ai.tool.call.result (OPTIONAL, sensitive)
    toolCallResult?.let { result ->
        span.addAttribute(SpanAttributes.Tool.Call.Result(result))
    }

    span.end(error.toSpanEndStatus(), verbose)
}
