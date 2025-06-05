package ai.koog.agents.mcp.config

public data class McpServerCommandConfig(
    override val name: String,
    val command: String,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
) : McpServerConfig {

    override val type: McpServerType = McpServerType.STDIO
}
