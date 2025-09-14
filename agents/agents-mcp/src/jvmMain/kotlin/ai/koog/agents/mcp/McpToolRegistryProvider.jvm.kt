package ai.koog.agents.mcp

import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered

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
