package ai.koog.agents.mcp.client

import ai.koog.agents.features.common.remote.client.engineFactoryProvider
import ai.koog.agents.mcp.McpTool
import ai.koog.agents.mcp.config.McpServerRemoteConfig
import io.ktor.client.*
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport

public class RemoteMcpClient(
    private val config: McpServerRemoteConfig,
    baseClient: HttpClient = HttpClient(engineFactoryProvider()),
) : McpClient {

    override val name: String
        get() = config.name

    override val client: Client
        get() = TODO("Not yet implemented")

    override fun connect(): Client {
        val implementation = Implementation("", "")

        val httpClient = HttpClient()
        SseClientTransport()
    }

    override fun getTools(): List<McpTool> {
        TODO("Not yet implemented")
    }

    override suspend fun close() {
        TODO("Not yet implemented")
    }
}
