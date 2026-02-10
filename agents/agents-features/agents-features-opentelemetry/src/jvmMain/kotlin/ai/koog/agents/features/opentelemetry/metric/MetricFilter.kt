package ai.koog.agents.features.opentelemetry.metric

/**
 * Represents a filter for OpenTelemetry metrics, specifying which attributes to retain for a given metric.
 *
 * @property metricName the name of the metric to filter
 * @property attributesKeysToRetain the set of attribute keys to retain for the filtered metric
 */
internal data class MetricFilter(val metricName: String, val attributesKeysToRetain: Set<String>)
