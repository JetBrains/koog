package ai.koog.agents.mcp.provider

import ai.koog.agents.mcp.McpTool
import ai.koog.agents.mcp.config.McpServerConfig
import ai.koog.agents.utils.Closeable
import io.modelcontextprotocol.kotlin.sdk.client.Client

public interface McpClient : Closeable {

    public val name: String

    public val version: String

    public val config: McpServerConfig

    public val client: Client

    public suspend fun connect()

    public suspend fun getTools(): List<McpTool>
}
