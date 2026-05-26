package ai.koog.agents.mcp

import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.mcp.metadata.McpMetadataKeys
import ai.koog.agents.mcp.metadata.McpServerInfo
import ai.koog.agents.mcp.metadata.McpTransportType
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.SSE
import io.ktor.http.Url
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.mcpStreamableHttpTransport
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_PROTOCOL_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.Tool

/**
 * A provider for creating tool registries that connect to Model Context Protocol (MCP) servers.
 *
 * This class facilitates the integration of MCP tools into the agent framework by:
 * 1. Connecting to MCP servers through various transport mechanisms (Streamable HTTP, stdio, SSE)
 * 2. Retrieving available tools from the MCP server
 * 3. Transforming MCP tools into the agent framework's Tool interface
 * 4. Registering the transformed tools in a ToolRegistry
 */
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
     * Configuration for connecting to an MCP server over the Streamable HTTP transport.
     *
     * Used as the receiver of the configuration lambda passed to [streamableHttp]. The [url] property
     * is required; the remaining properties have sensible defaults and are optional.
     *
     * Example:
     * ```kotlin
     * val registry = McpToolRegistryProvider.streamableHttp {
     *     url = "http://localhost:3000/mcp"
     * }
     * ```
     */
    public class StreamableHttpConfig {
        /** MCP server URL (required). */
        public lateinit var url: String

        /**
         * HttpClient used for the MCP connection. Must have the Ktor `SSE` plugin installed
         * (the Streamable HTTP transport relies on it).
         *
         * If `null` (default), a private [HttpClient] is created internally and closed automatically
         * when the underlying MCP transport closes. If a custom client is provided, the caller
         * retains full responsibility for its lifecycle (we do not close it).
         *
         * Example:
         * ```kotlin
         * val httpClient = HttpClient { install(SSE) }
         * ```
         */
        public var httpClient: HttpClient? = null

        /** Custom MCP tool descriptor parser. */
        public var mcpToolParser: McpToolDescriptorParser = DefaultMcpToolDescriptorParser

        /** Client name reported to the server. */
        public var name: String = DEFAULT_MCP_CLIENT_NAME

        /** Client version reported to the server. */
        public var version: String = DEFAULT_MCP_CLIENT_VERSION
    }

    /**
     * Creates a ToolRegistry from a Streamable HTTP MCP server.
     *
     * This is the recommended way to connect to remote MCP servers. Streamable HTTP supports
     * bidirectional communication, session management, and reconnection.
     *
     * If [StreamableHttpConfig.httpClient] is not set, a default [HttpClient] with the `SSE` plugin
     * is created internally and closed automatically when the MCP transport closes. To reuse a
     * single client across multiple MCP connections, set it explicitly — in that case the caller
     * is responsible for closing it.
     *
     * @param block Configuration block for the Streamable HTTP connection.
     * @return A ToolRegistry containing all tools from the MCP server.
     */
    public suspend fun streamableHttp(
        block: StreamableHttpConfig.() -> Unit
    ): ToolRegistry {
        val config = StreamableHttpConfig().apply(block)
        val httpClient = config.httpClient ?: HttpClient { install(SSE) }
        val ownsClient = config.httpClient == null
        val transport = httpClient.mcpStreamableHttpTransport(config.url)
        if (ownsClient) {
            transport.onClose { httpClient.close() }
        }
        val mcpClient = Client(clientInfo = Implementation(config.name, config.version))
        mcpClient.connect(transport)
        return fromClient(
            mcpClient = mcpClient,
            serverInfo = McpServerInfo(url = config.url),
            mcpToolParser = config.mcpToolParser,
        )
    }

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
     * @param serverInfo Information about the MCP server.
     * @return A ToolRegistry containing all tools from the MCP server.
     */
    public suspend fun fromClient(
        mcpClient: Client,
        serverInfo: McpServerInfo,
        mcpToolParser: McpToolDescriptorParser = DefaultMcpToolDescriptorParser,
    ): ToolRegistry {
        val sdkTools = mcpClient.listTools().tools
        return buildToolRegistry(sdkTools, mcpToolParser, serverInfo, mcpClient)
    }

    @OptIn(InternalAgentsApi::class)
    private fun buildToolRegistry(
        sdkTools: List<Tool>,
        mcpToolParser: McpToolDescriptorParser,
        serverInfo: McpServerInfo,
        mcpClient: Client
    ): ToolRegistry = ToolRegistry {
        sdkTools.forEach { sdkTool ->
            try {
                val toolDescriptor = mcpToolParser.parse(sdkTool)
                val toolMetaData = mapOf(
                    McpMetadataKeys.ToolId to sdkTool.name,
                    McpMetadataKeys.McpProtocolVersion to LATEST_PROTOCOL_VERSION,
                    McpMetadataKeys.McpTransportType to when (mcpClient.transport) {
                        is StreamableHttpClientTransport -> McpTransportType.StreamableHttp
                        is SseClientTransport -> McpTransportType.Tcp
                        is StdioClientTransport -> McpTransportType.Stdio
                        else -> {
                            logger.warn { "Unknown transport type: ${mcpClient.transport?.let { it::class.simpleName }}" }
                            McpTransportType.Unknown
                        }
                    }.value,
                    McpMetadataKeys.McpSessionId to "",
                    McpMetadataKeys.ServerUrl to serverInfo.url.orEmpty(),
                    McpMetadataKeys.ServerPort to getPort(serverInfo.url),
                )
                tool(McpTool(mcpClient, toolDescriptor, toolMetaData))
            } catch (e: Throwable) {
                logger.error(e) { "Failed to parse descriptor parameters for tool: ${sdkTool.name}" }
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

    private fun getPort(url: String?): String {
        return runCatching { url?.let(::Url)?.port?.toString() }.getOrNull().orEmpty()
    }
}
