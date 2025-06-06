package ai.koog.agents.mcp.client

import ai.koog.agents.mcp.McpTool
import io.modelcontextprotocol.kotlin.sdk.client.Client

public class ProcessMcpClient(
    public override val name: String,
    private val process: Process
) : McpClient {

    override val client: Client
        get() = TODO("Not yet implemented")

    override suspend fun connect() {
        TODO("Not yet implemented")
    }

    override suspend fun getTools(): List<McpTool> {
        TODO("Not yet implemented")
    }

    override suspend fun close() {
        TODO("Not yet implemented")
    }
}