package ai.koog.ktor

import ai.koog.agents.mcp.DefaultMcpToolDescriptorParser
import ai.koog.agents.mcp.McpToolDescriptorParser
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.mcp.McpToolRegistryProvider.DEFAULT_MCP_CLIENT_NAME
import ai.koog.agents.mcp.McpToolRegistryProvider.DEFAULT_MCP_CLIENT_VERSION
import ai.koog.agents.mcp.defaultStdioTransport
import io.modelcontextprotocol.kotlin.sdk.client.Client
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Configuration class for MCPTools that manages the integration of various tool registries
 * into the system. Provides methods to process tools using different transport mechanisms.
 *
 * @param agentConfig Configuration for the Koog agent server, which includes tool registry details.
 */
public class McpToolsConfig(private val agentConfig: KoogAgentsConfig.AgentConfig) {
    private val mutex = Mutex()

    /**
     * Processes a given `Process` instance to register tools from an MCP server.
     *
     * The method leverages a transport protocol for communication with the MCP server, parses the tool
     * definitions using the specified or default `McpToolDescriptorParser`, and registers the tools
     * in the agent's tool registry under a given client name and version.
     *
     * @param process The `Process` instance representing the MCP server communication process.
     *               Its input and output streams will be used for the transport.
     * @param mcpToolParser The parser that converts MCP tool definitions to standardized descriptors.
     *                      Defaults to `DefaultMcpToolDescriptorParser`.
     * @param name The name of the MCP client for identifying the source of the tools.
     *             Defaults to `DEFAULT_MCP_CLIENT_NAME`.
     * @param version The version of the MCP client for identifying the source of the tools.
     *                Defaults to `DEFAULT_MCP_CLIENT_VERSION`.
     */
    public fun process(
        process: Process,
        mcpToolParser: McpToolDescriptorParser = DefaultMcpToolDescriptorParser,
        name: String = DEFAULT_MCP_CLIENT_NAME,
        version: String = DEFAULT_MCP_CLIENT_VERSION,
    ) {
        agentConfig.scope.launch {
            val transport = McpToolRegistryProvider.fromTransport(
                transport = McpToolRegistryProvider.defaultStdioTransport(process),
                mcpToolParser = mcpToolParser,
                name = name,
                version = version,
            )
            mutex.withLock { agentConfig.toolRegistry += transport }
        }
    }

    /**
     * Registers tools from an MCP server using server-sent events (SSE) transport.
     *
     * This method establishes an SSE connection to an MCP server at the given URL to retrieve and register
     * tools in the tool registry.
     *
     * @param url The URL to establish the SSE connection with the MCP server.
     * @param mcpToolParser A parser for converting the MCP SDK tool definitions into a standardized format.
     * Defaults to `DefaultMcpToolDescriptorParser`.
     * @param name The name of the MCP client. Defaults to `DEFAULT_MCP_CLIENT_NAME`.
     * @param version The version of the MCP client. Defaults to `DEFAULT_MCP_CLIENT_VERSION`.
     */
    public fun sse(
        url: String,
        mcpToolParser: McpToolDescriptorParser = DefaultMcpToolDescriptorParser,
        name: String = DEFAULT_MCP_CLIENT_NAME,
        version: String = DEFAULT_MCP_CLIENT_VERSION,
    ) {
        agentConfig.scope.launch {
            val transport = McpToolRegistryProvider.fromTransport(
                transport = McpToolRegistryProvider.defaultSseTransport(url),
                mcpToolParser = mcpToolParser,
                name = name,
                version = version,
            )
            mutex.withLock { agentConfig.toolRegistry += transport }
        }
    }

    /**
     * Registers tools from an existing MCP client into the tool registry.
     *
     * This method retrieves tools from the given MCP client, parses their definitions using
     * the provided or default MCP tool descriptor parser, and adds them to the tool registry.
     *
     * @param mcpClient The MCP client connected to an MCP server, providing access to tools.
     * @param mcpToolParser The parser used to convert raw tool information into standardized tool descriptors.
     * Defaults to the standard parser implementation.
     */
    public fun client(
        mcpClient: Client,
        mcpToolParser: McpToolDescriptorParser = DefaultMcpToolDescriptorParser
    ) {
        agentConfig.scope.launch {
            val fromClient = McpToolRegistryProvider.fromClient(mcpClient, mcpToolParser)
            mutex.withLock { agentConfig.toolRegistry += fromClient }
        }
    }
}

/**
 * Configures the MCP (Modular Configuration Protocol) tools for the agent with the provided configuration block.
 *
 * @param configure A suspend lambda used to configure the MCPToolsConfig instance.
 */
public fun KoogAgentsConfig.AgentConfig.mcp(configure: McpToolsConfig.() -> Unit) {
    McpToolsConfig(this).configure()
}
