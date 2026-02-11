package ai.koog.agents.features.opentelemetry.metric

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import kotlinx.datetime.Instant

internal sealed interface MetricEvent {
    val id: String
    val timestamp: Instant
    val metricName: String
    val attributes: List<Attribute>

    fun withAttributes(attributes: List<Attribute>): MetricEvent
}

internal open class BaseMetricEvent(
    override val id: String,
    override val timestamp: Instant,
    override val metricName: String,
    override val attributes: List<Attribute>
) : MetricEvent {
    override fun withAttributes(attributes: List<Attribute>): MetricEvent {
        return BaseMetricEvent(id, timestamp, metricName, attributes)
    }
}

internal data class CounterMetricEvent(
    override val id: String,
    override val timestamp: Instant,
    override val metricName: String,
    override val attributes: List<Attribute>,
    val value: Long
) : BaseMetricEvent(id, timestamp, metricName, attributes) {
    override fun withAttributes(attributes: List<Attribute>) = copy(attributes = attributes)
}

internal data class HistogramMetricEvent(
    override val id: String,
    override val timestamp: Instant,
    override val metricName: String,
    override val attributes: List<Attribute>,
    val value: Double
) : BaseMetricEvent(id, timestamp, metricName, attributes) {
    override fun withAttributes(attributes: List<Attribute>) = copy(attributes = attributes)
}
