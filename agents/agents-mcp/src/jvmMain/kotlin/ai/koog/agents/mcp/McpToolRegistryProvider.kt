package ai.koog.agents.mcp

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.mcp.parser.DefaultMcpToolDescriptorParser
import ai.koog.agents.mcp.parser.McpToolDescriptorParser
import ai.koog.agents.mcp.parser.ToolConverter.toTools
import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.shared.Transport

/**
 * A provider for creating tool registries that connect to Model Context Protocol (MCP) servers.
 *
 * This class facilitates the integration of MCP tools into the agent framework by:
 * 1. Connecting to MCP servers through various transport mechanisms (stdio, SSE)
 * 2. Retrieving available tools from the MCP server
 * 3. Transforming MCP tools into the agent framework's Tool interface
 * 4. Registering the transformed tools in a ToolRegistry
 */
public object McpToolRegistryProvider {

    private val logger = KotlinLogging.logger (McpToolRegistryProvider::class.qualifiedName!!)

    /**
     * Default name for the MCP client when connecting to an MCP server.
     */
    public const val DEFAULT_MCP_CLIENT_NAME: String = "mcp-client-cli"

    /**
     * Default version for the MCP client when connecting to an MCP server.
     */
    public const val DEFAULT_MCP_CLIENT_VERSION: String = "1.0.0"


    /**
     * Creates a ToolRegistry with tools from an existing MCP client.
     *
     * This method retrieves all available tools from the MCP server using the provided client,
     * transforms them into the agent framework's Tool interface, and registers them in a ToolRegistry.
     *
     * @param mcpClient The MCP client connected to an MCP server.
     * @return A ToolRegistry containing all tools from the MCP server.
     */
    public suspend fun fromClient(
        mcpClient: Client,
        mcpToolParser: McpToolDescriptorParser = DefaultMcpToolDescriptorParser,
    ): ToolRegistry {
        val mcpTools = mcpClient.listTools()?.toTools(client = mcpClient, parser = mcpToolParser) ?: emptyList()
        return ToolRegistry {
            tools(mcpTools)
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
    public suspend fun fromTransport(
        transport: Transport,
        mcpToolParser: McpToolDescriptorParser = DefaultMcpToolDescriptorParser,
        name: String = DEFAULT_MCP_CLIENT_NAME,
        version: String = DEFAULT_MCP_CLIENT_VERSION,
    ): ToolRegistry {
        val mcpClient = Client(clientInfo = Implementation(name = name, version = version))
        mcpClient.connect(transport)

        return fromClient(mcpClient, mcpToolParser)
    }
}
