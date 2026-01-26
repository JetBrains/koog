package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.agents.features.opentelemetry.attribute.McpAttributes
import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes
import ai.koog.agents.features.opentelemetry.integration.mcp.McpMethod

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
 * @param method The MCP method being invoked.
 * @param serverAddress The server address for client spans (recommended).
 * @param serverPort The server port for client spans (recommended).
 * @param sessionId The MCP session identifier (recommended when part of a session).
 * @param mcpProtocolVersion The MCP protocol version in use (recommended).
 * @param mcpTransportType The transport type used for communication (recommended).
 * @return A started GenAIAgentSpan configured for MCP client operations.
 */
internal fun GenAIAgentSpan.enrichExecuteToolSpanWithMcpAttrs(
    toolName: String,
    method: McpMethod,
    serverAddress: String? = null,
    serverPort: Int? = null,
    sessionId: String? = null,
    mcpProtocolVersion: String,
    mcpTransportType: String,
): GenAIAgentSpan {
    // mcp.method.name (REQUIRED)
    addAttribute(McpAttributes.Mcp.Method.Name(method.methodName))

    // gen_ai.operation.name (RECOMMENDED for tool calls)
    addAttribute(SpanAttributes.Operation.Name(SpanAttributes.Operation.OperationNameType.EXECUTE_TOOL))

    // gen_ai.tool.name (CONDITIONALLY REQUIRED)
    addAttribute(SpanAttributes.Tool.Name(toolName))

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
