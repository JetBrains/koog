package ai.koog.agents.mcp.provider

import ai.koog.agents.mcp.config.McpServerCommandConfig
import ai.koog.agents.mcp.config.McpServerConfig
import io.github.oshai.kotlinlogging.KotlinLogging

internal class McpClientProvider {

    companion object {
        private val logger = KotlinLogging.logger("ai.koog.agents.mcp.provider.McpClientProvider")
    }

    fun provideClient(config: McpServerConfig): McpClient {
        val mcpClient: McpClient =
            when (config) {
                is CommandBaseMcpClient ->
                else ->
            }

        return
    }
}