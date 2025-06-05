package ai.koog.agents.mcp.provider

import ai.koog.agents.mcp.McpTool
import ai.koog.agents.utils.Closeable
import io.modelcontextprotocol.kotlin.sdk.client.Client

public interface McpClient : Closeable {

    public fun connect(): Client

    public fun getTools(): List<McpTool>
}
