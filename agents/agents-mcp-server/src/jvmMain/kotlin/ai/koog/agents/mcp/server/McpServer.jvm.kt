package ai.koog.agents.mcp.server

import ai.koog.agents.core.tools.ToolRegistry
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered

/**
 * Starts a new MCP server with the passed [tools] that listens to stdin and writes to stdout.
 */
public suspend fun startStdioMcpServer(tools: ToolRegistry): Server {
    return configureMcpServer(tools)
        .apply {
            connect(
                StdioServerTransport(
                    inputStream = System.`in`.asSource().buffered(),
                    outputStream = System.out.asSink().buffered()
                )
            )
        }
}
