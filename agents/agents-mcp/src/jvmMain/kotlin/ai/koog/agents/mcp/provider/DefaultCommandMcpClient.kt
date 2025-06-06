package ai.koog.agents.mcp.provider

import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.mcp.config.McpServerCommandConfig
import io.modelcontextprotocol.kotlin.sdk.client.Client

class DefaultCommandMcpClient(config: McpServerCommandConfig) : CommandBaseMcpClient(config, CommandMcpClientType.UNKNOWN) {

}