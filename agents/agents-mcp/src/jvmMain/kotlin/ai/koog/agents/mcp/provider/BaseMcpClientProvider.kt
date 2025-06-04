package ai.koog.agents.mcp.provider

import io.modelcontextprotocol.kotlin.sdk.client.Client

internal abstract class BaseMcpClientProvider : McpClientProvider {

    protected abstract fun connect(): Client

}
