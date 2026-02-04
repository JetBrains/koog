package ai.koog.agents.features.opentelemetry.metric

internal data class MetricFilter(val metricName: String, val attributesKeysToRetain: Set<String>)

internal data class ToolCallMapper(
    val allowedToolCallNames: Set<String>,
    val defaultToolCallName: String,
)
