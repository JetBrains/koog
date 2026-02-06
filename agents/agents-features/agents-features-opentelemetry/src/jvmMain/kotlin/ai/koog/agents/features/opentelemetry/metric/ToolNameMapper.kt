package ai.koog.agents.features.opentelemetry.metric

internal interface ToolNameMapper {
    fun map(toolName: String): String
}

internal class AllowlistToolNameMapper(
    val allowedToolNames: Set<String>,
    val fallbackToolName: String,
) : ToolNameMapper {
    override fun map(toolName: String) =
        if (toolName in allowedToolNames) toolName else fallbackToolName
}

internal class NoopToolNameMapper : ToolNameMapper {
    override fun map(toolName: String) = toolName
}
