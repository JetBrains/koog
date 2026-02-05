package ai.koog.agents.features.opentelemetry.metric

internal data class MetricFilter(val metricName: String, val attributesKeysToRetain: Set<String>)

internal interface ToolCallMapper {
    fun map(toolName: String): String
}

internal class ConfiguredToolCallMapper(
    val allowedToolCallNames: Set<String>,
    val defaultToolCallName: String,
) : ToolCallMapper {
    override fun map(toolName: String) =
        if (toolName in allowedToolCallNames) toolName else defaultToolCallName
}

internal class DefaultToolCallMapper : ToolCallMapper {
    override fun map(toolName: String) = toolName
}
