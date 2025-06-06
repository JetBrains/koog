package ai.koog.agents.mcp.provider

import ai.koog.agents.mcp.McpTool
import ai.koog.agents.mcp.config.McpServerRemoteConfig
import io.modelcontextprotocol.kotlin.sdk.client.Client

public class RemoteMcpClient(
    override val config: McpServerRemoteConfig,
) : McpClient {

    override fun connect(): Client {
        TODO("Not yet implemented")
    }

    override fun getTools(): List<McpTool> {
        TODO("Not yet implemented")
    }

    override suspend fun close() {
        TODO("Not yet implemented")
    }
}
