package ai.koog.agents.mcp.provider

import ai.koog.agents.mcp.McpToolRegistryProvider
import io.modelcontextprotocol.kotlin.sdk.client.Client

public class DockerMcpClientProvider : BaseMcpClientProvider() {

    override fun connect(): Client {
        McpToolRegistryProvider.defaultStdioTransport(process)

    }

    private fun startDockerProcess(): Process {

    }

    override suspend fun close() {

    }
}
