package ai.koog.agents.mcp.config

import io.ktor.http.URLProtocol

public data class McpServerRemoteConfig(
    override val name: String,
    val host: String,
    val port: Int,
    val protocol: URLProtocol,
) : McpServerConfig {

    internal companion object {
        internal val defaultProtocol = URLProtocol.HTTPS
    }

    override val type: McpServerType = McpServerType.SSE
}
