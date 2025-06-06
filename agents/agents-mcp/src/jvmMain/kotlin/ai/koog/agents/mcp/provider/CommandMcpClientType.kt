package ai.koog.agents.mcp.provider

public enum class CommandMcpClientType(public val command: String) {
    UNKNOWN(""),
    DOCKER("docker"),
    NPX("npx"),
    UV("uv"),
    UVX("uvx"),
}