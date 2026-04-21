package ai.koog.agents.features.opentelemetry.metric

internal data class MetricFilter(val metricName: String, val attributesKeysToRetain: Set<String>)
