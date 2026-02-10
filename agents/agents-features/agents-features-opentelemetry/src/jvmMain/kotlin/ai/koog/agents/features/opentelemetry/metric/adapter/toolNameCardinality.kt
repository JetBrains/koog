package ai.koog.agents.features.opentelemetry.metric.adapter

import ai.koog.agents.features.opentelemetry.attribute.GenAIAttributes
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetryConfig
import ai.koog.agents.features.opentelemetry.metric.CounterMetricEvent
import ai.koog.agents.features.opentelemetry.metric.GenAIMetrics
import ai.koog.agents.features.opentelemetry.metric.HistogramMetricEvent
import ai.koog.agents.features.opentelemetry.metric.KoogMetrics
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
        override fun process(metricEvent: MetricEvent): MetricEvent {
            if (metricEvent.metricName == KoogMetrics.Tool.Count.name ||
                metricEvent.metricName == GenAIMetrics.Client.Operation.Duration.name
            ) {
                val expectedAttributeKey = GenAIAttributes.Tool.Name("").key
                val toolNameAttribute = metricEvent.attributes.find { attribute -> attribute.key == expectedAttributeKey }

                if (toolNameAttribute != null && toolNameAttribute.value !in allowedToolNames) {
                    val updatedAttributes = metricEvent.attributes.map { attribute ->
                        if (attribute.key == expectedAttributeKey) {
                            GenAIAttributes.Tool.Name(fallbackToolName)
                        } else {
                            attribute
                        }
                    }

                    when (metricEvent) {
                        is CounterMetricEvent -> {
                            return metricEvent.copy(attributes = updatedAttributes)
                        }

                        is HistogramMetricEvent -> {
                            return metricEvent.copy(attributes = updatedAttributes)
                        }
                    }
                }
            }

            return metricEvent
        }
    })
}
