package ai.koog.agents.mcp.metadata

/**
 * Metadata keys for tools provided by MCP servers.
 */
public object McpMetadataKeys {
    /**
     * Required key to identify an MCP tool.
     */
    public const val ToolId: String = "koog.mcp.tool.id"

    /**
     * The URL where the server is accessible, if applicable (e.g., "http://localhost").
     */
    public const val ServerUrl: String = "koog.mcp.server.url"

    /**
     * The port number on which the server listens, if applicable.
     */
    public const val ServerPort: String = "koog.mcp.server.port"

    /**
     * Optional server-provided instructions or documentation for using its tools.
     */
    public const val Instructions: String = "koog.mcp.server.instructions"

    /**
     * The MCP protocol version supported by this server connection.
     */
    public const val McpProtocolVersion: String = "koog.mcp.protocol.version"

    /**
     * The transport protocol type used for this connection (e.g., pipe for stdio, tcp for HTTP).
     */
    public const val McpTransportType: String = "koog.mcp.transport.type"

    /**
     * Unique identifier for the current MCP session, if session management is enabled.
     * TODO: support [McpSessionId] when it would be available in the sdk client
     */
    public const val McpSessionId: String = "koog.mcp.session.id"
}

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
    Stdio("pipe"),

    /**
     * TCP-based transport using HTTP or other network protocols.
     *
     * This transport enables communication with remote MCP servers over network
     * connections, including HTTP, SSE (Server-Sent Events), or custom TCP protocols.
     * Suitable for distributed architectures and cloud-based MCP servers.
     */
    Tcp("tcp"),
}
