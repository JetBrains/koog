package ai.koog.agents.core.feature.handler.tool

import ai.koog.agents.core.annotation.InternalAgentsApi

/**
 * Comprehensive metadata describing an MCP server connection.
 *
 * This class captures both the server's identity and the active connection details,
 * including protocol version, transport type, and session information. This metadata
 * is essential for tracking server capabilities, managing connections, and debugging
 * tool execution issues.
 *
 * @property serverUrl The URL where the server is accessible, if applicable (e.g., "http://localhost").
 * @property serverPort The port number on which the server listens, if applicable.
 * @property siteUrl The server's public website URL, if provided by the server.
 * @property instructions Optional server-provided instructions or documentation for using its tools.
 * @property mcpProtocolVersion The MCP protocol version supported by this server connection.
 * @property mcpTransportType The transport protocol type used for this connection (e.g., pipe for stdio, tcp for HTTP).
 * TODO: support [sessionId] when it would be available in the sdk client
 * @property sessionId Unique identifier for the current MCP session, if session management is enabled.
 */
@InternalAgentsApi
public class McpServerMeta(
    public val serverUrl: String?,
    public val serverPort: Int?,
    public val siteUrl: String?,
    public val instructions: String?,
    public val mcpProtocolVersion: String,
    public val mcpTransportType: McpTransportType,
    public val sessionId: String?,
)

/**
 * Information about an MCP server instance.
 */
public class McpServerInfo(
    public val url: String? = null,
    public val command: String? = null,
)

/**
 * Enumeration of supported MCP server transport protocol types.
 *
 * This enum defines the different communication mechanisms that can be used
 * to establish connections with MCP servers. Each transport type has distinct
 * characteristics and is suitable for different deployment scenarios.
 *
 * @property value The canonical name of the transport protocol.
 */
public enum class McpTransportType(public val value: String) {
    /**
     * Pipe-based transport using standard input/output streams.
     *
     * This transport is typically used for local MCP servers running as separate
     * processes that communicate via stdio. Suitable for development and local tools.
     */
    Pipe("pipe"),

    /**
     * TCP-based transport using HTTP or other network protocols.
     *
     * This transport enables communication with remote MCP servers over network
     * connections, including HTTP, SSE (Server-Sent Events), or custom TCP protocols.
     * Suitable for distributed architectures and cloud-based MCP servers.
     */
    Tcp("tcp"),
}
