package ai.koog.agents.mcp.provider

import ai.koog.agents.mcp.config.McpServerCommandConfig
import ai.koog.agents.mcp.config.McpServerConfig
import ai.koog.agents.mcp.config.McpServerRemoteConfig
import io.github.oshai.kotlinlogging.KotlinLogging

internal class McpClientProvider {

    companion object {
        private val logger = KotlinLogging.logger("ai.koog.agents.mcp.provider.McpClientProvider")
    }

    fun provideClient(config: McpServerConfig): McpClient {
        val mcpClient: McpClient =
            when (config) {
                is McpServerRemoteConfig -> {
                    RemoteMcpClient(config)
                }
                is McpServerCommandConfig -> {
                    if (config.command == CommandMcpClientType.DOCKER.command) {
                        DockerCommandMcpClient(config)
                    }
                    else {
                        DefaultCommandMcpClient(config)
                    }
                }
                else -> {
                    error("Unsupported MCP server config type: ${config::class.simpleName}")
                }
            }

        logger.debug { "Defined MCP client to use: $mcpClient" }
        return mcpClient
    }
}
