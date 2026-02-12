package ai.koog.agents.features.opentelemetry.metric.adapter

import ai.koog.agents.features.opentelemetry.attribute.GenAIAttributes
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetryConfig
import ai.koog.agents.features.opentelemetry.metric.MetricEvent

private const val FALLBACK_TOOL_NAME = "filtered"

/**
 * Restricts tool names in the attributes' metric and sets the fallback tool name when a tool is not allowed.
 * Helps to manage cardinality of the metric.
 *
 * @param allowedToolNames A set of allowed tool names
 * @param fallbackToolName The fallback / default tool name if not in the allowed set
 */
public fun OpenTelemetryConfig.restrictToolNameCardinality(
    allowedToolNames: Set<String>,
    fallbackToolName: String = FALLBACK_TOOL_NAME,
) {
    addMetricAdapter(object : MetricAdapter() {
        override fun <T : MetricEvent<T>> process(metricEvent: T): T {
            val toolNameKey = GenAIAttributes.Tool.Name("").key
            val toolName = metricEvent.attributes.find { it.key == toolNameKey }

            if (toolName == null || toolName.value in allowedToolNames) {
                return metricEvent
            }

            return metricEvent.withAttributes(
                metricEvent.attributes.map {
                    if (it.key == toolNameKey) {
                        GenAIAttributes.Tool.Name(fallbackToolName)
                    } else {
                        it
                    }
                }
            )
        }
    })
}
