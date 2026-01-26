package ai.koog.agents.mcp

import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.feature.handler.tool.McpServerInfo
import ai.koog.agents.core.feature.handler.tool.McpServerMeta
import ai.koog.agents.core.feature.handler.tool.McpTransportType
import ai.koog.agents.core.tools.ToolRegistry
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.SSE
import io.ktor.http.Url
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_PROTOCOL_VERSION

/**
 * A provider for creating tool registries that connect to Model Context Protocol (MCP) servers.
 *
 * This class facilitates the integration of MCP tools into the agent framework by:
 * 1. Connecting to MCP servers through various transport mechanisms (stdio, SSE)
 * 2. Retrieving available tools from the MCP server
 * 3. Transforming MCP tools into the agent framework's Tool interface
 * 4. Registering the transformed tools in a ToolRegistry
 */
@OptIn(InternalAgentsApi::class)
public object McpToolRegistryProvider {
    private val logger = KotlinLogging.logger {}

    /**
     * Default name for the MCP client when connecting to an MCP server.
     */
    public const val DEFAULT_MCP_CLIENT_NAME: String = "mcp-client-cli"

    /**
     * Default version for the MCP client when connecting to an MCP server.
     */
    public const val DEFAULT_MCP_CLIENT_VERSION: String = "1.0.0"

    /**
     * Creates a default server-sent events (SSE) transport from a provided URL.
     *
     * @param url The URL to be used for establishing an SSE connection.
     * @return An instance of SseClientTransport configured with the given URL.
     */
    public fun defaultSseTransport(url: String, baseClient: HttpClient = HttpClient()): SseClientTransport {
        // Setup SSE transport using the HTTP client
        return SseClientTransport(
            client = baseClient.config {
                install(SSE)
            },
            urlString = url,
        )
    }

    /**
     * Creates a ToolRegistry with tools from an existing MCP client.
     *
     * This method retrieves all available tools from the MCP server using the provided client,
     * transforms them into the agent framework's Tool interface, and registers them in a ToolRegistry.
     *
     * @param mcpClient The MCP client connected to an MCP server.
     * @return A ToolRegistry containing all tools from the MCP server.
     */
    @Deprecated("Use fromClient with serverInfo param")
    public suspend fun fromClient(
        mcpClient: Client,
        mcpToolParser: McpToolDescriptorParser = DefaultMcpToolDescriptorParser,
    ): ToolRegistry {
        return fromClient(mcpClient, McpServerInfo(url = null, command = null), mcpToolParser)
    }

    /**
     * Creates a ToolRegistry with tools from an existing MCP client.
     *
     * This method retrieves all available tools from the MCP server using the provided client,
     * transforms them into the agent framework's Tool interface, and registers them in a ToolRegistry.
     *
     * @param mcpClient The MCP client connected to an MCP server.
     * @param serverInfo Information about the MCP server.
     * @return A ToolRegistry containing all tools from the MCP server.
     */
    public suspend fun fromClient(
        mcpClient: Client,
        serverInfo: McpServerInfo,
        mcpToolParser: McpToolDescriptorParser = DefaultMcpToolDescriptorParser,
    ): ToolRegistry {
        val sdkTools = mcpClient.listTools().tools
        return ToolRegistry {
            sdkTools.forEach { sdkTool ->
                try {
                    val toolDescriptor = mcpToolParser.parse(sdkTool)
                    val serverMeta = McpServerMeta(
                        serverUrl = serverInfo.url,
                        serverPort = runCatching { serverInfo.url?.let(::Url)?.port }.getOrNull(),
                        mcpTransportType = when (mcpClient.transport) {
                            is SseClientTransport -> McpTransportType.Tcp
                            is StdioClientTransport -> McpTransportType.Pipe
                            else -> error("Unexpected null for client transport: ${mcpClient.transport}")
                        },
                        siteUrl = null,
                        instructions = mcpClient.serverInstructions,
                        // TODO: support sessionId when it would be exposed in the sdk client
                        sessionId = null,
                        // TODO: support actual server protocol version when it would be exposed in the sdk client
                        mcpProtocolVersion = LATEST_PROTOCOL_VERSION,
                    )
                    tool(McpTool(mcpClient, toolDescriptor, serverMeta))
                } catch (e: Throwable) {
                    logger.error(e) { "Failed to parse descriptor parameters for tool: ${sdkTool.name}" }
                }
            }
        }
    }

    /**
     * Creates a ToolRegistry with tools from an MCP server using provided transport for communication.
     *
     * This method establishes a connection to an MCP server through provided transport.
     * It's typically used when the MCP server is running as a separate process (e.g., a Docker container or a CLI tool).
     *
     * @param transport The transport to use.
     * @param name The name of the MCP client.
     * @param version The version of the MCP client.
     * @return A ToolRegistry containing all tools from the MCP server.
     */
    @Deprecated("Use from fromTransport with `serverInfo`")
    public suspend fun fromTransport(
        transport: Transport,
        mcpToolParser: McpToolDescriptorParser = DefaultMcpToolDescriptorParser,
        name: String = DEFAULT_MCP_CLIENT_NAME,
        version: String = DEFAULT_MCP_CLIENT_VERSION,
    ): ToolRegistry {
        // Create the MCP client
        val mcpClient = Client(clientInfo = Implementation(name = name, version = version))

        // Connect to the MCP server
        mcpClient.connect(transport)

        @Suppress("DEPRECATION")
        return fromClient(mcpClient, mcpToolParser)
    }

    /**
     * Creates a ToolRegistry with tools from an MCP server using provided transport for communication.
     *
     * This method establishes a connection to an MCP server through provided transport.
     * It's typically used when the MCP server is running as a separate process (e.g., a Docker container or a CLI tool).
     *
     * @param transport The transport to use.
     * @param serverInfo Information about the MCP server.
     * @param name The name of the MCP client.
     * @param version The version of the MCP client.
     * @return A ToolRegistry containing all tools from the MCP server.
     */
    public suspend fun fromTransport(
        transport: Transport,
        serverInfo: McpServerInfo,
        mcpToolParser: McpToolDescriptorParser = DefaultMcpToolDescriptorParser,
        name: String = DEFAULT_MCP_CLIENT_NAME,
        version: String = DEFAULT_MCP_CLIENT_VERSION,
    ): ToolRegistry {
        return fromClient(
            mcpClient = Client(clientInfo = Implementation(name = name, version = version)).apply {
                connect(transport)
            },
            serverInfo = serverInfo,
            mcpToolParser = mcpToolParser,
        )
    }

    /**
     * Creates a ToolRegistry with tools from an MCP server using a server-sent events (SSE) transport.
     */
    public suspend fun fromSseUrl(
        sseUrl: String,
        clientInfo: Implementation = Implementation(DEFAULT_MCP_CLIENT_NAME, DEFAULT_MCP_CLIENT_VERSION),
        mcpToolParser: McpToolDescriptorParser = DefaultMcpToolDescriptorParser
    ): ToolRegistry {
        return fromClient(
            mcpClient = Client(clientInfo = clientInfo).apply {
                connect(defaultSseTransport(sseUrl))
            },
            serverInfo = McpServerInfo(url = sseUrl),
            mcpToolParser = mcpToolParser
        )
    }
}
