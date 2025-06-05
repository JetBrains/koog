package ai.koog.agents.mcp.provider

import ai.koog.agents.mcp.McpTool
import ai.koog.agents.mcp.config.McpServerConfigParser
import ai.koog.agents.mcp.config.McpServerCommandConfig
import ai.koog.agents.mcp.config.McpServerConfig
import io.modelcontextprotocol.kotlin.sdk.client.Client

public abstract class CommandBaseMcpClient(private val mcpServerConfig: McpServerConfig) : McpClient {

    private val clientProvider = McpClientProvider()

    //region Config

    private var _commandConfig: McpServerCommandConfig? = null

    public val commandConfig: McpServerCommandConfig
        get() = _commandConfig ?: error("mcp server config is not set")

    //endregion Config

    //region Connect

    override fun connect(): Client {
        // Create Client
        val client = clientProvider.provideClient(mcpServerConfig)
        return client
    }

    //endregion Connect

    //region Tools

    override fun getTools(): List<McpTool> {

    }

    //endregion Tools
}