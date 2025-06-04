package ai.koog.agents.mcp.config

import kotlinx.serialization.Serializable

@Serializable
public data class McpServerConfig(
    val name: String,
    val type: McpServerType?,
    val command: String,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
)
