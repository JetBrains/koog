package ai.koog.agents.mcp

import ai.koog.agents.core.feature.handler.tool.McpServerInfo
import ai.koog.agents.core.tools.ToolRegistry
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlin.jvm.optionals.getOrNull

/**
 * Creates a default standard input/output transport for a provided process.
 *
 * @param process The process whose input and output streams will be used for communication.
 * @return A [StdioClientTransport] configured to communicate with the process using its standard input and output.
 */
public fun McpToolRegistryProvider.defaultStdioTransport(process: Process): StdioClientTransport {
    return StdioClientTransport(
        input = process.inputStream.asSource().buffered(),
        output = process.outputStream.asSink().buffered()
    )
}

/**
 * Creates a Mcp [ToolRegistry] instance from a process using default standard input/output transport.
 */
public suspend fun McpToolRegistryProvider.fromProcess(
    process: Process,
    clientInfo: Implementation = Implementation(DEFAULT_MCP_CLIENT_NAME, DEFAULT_MCP_CLIENT_VERSION),
    mcpToolParser: McpToolDescriptorParser = DefaultMcpToolDescriptorParser
): ToolRegistry {
    return fromClient(
        mcpClient = Client(clientInfo).apply {
            connect(defaultStdioTransport(process))
        },
        serverInfo = McpServerInfo(command = runCatching { process.info()?.commandLine()?.getOrNull() }.getOrNull()),
        mcpToolParser = mcpToolParser
    )
}
