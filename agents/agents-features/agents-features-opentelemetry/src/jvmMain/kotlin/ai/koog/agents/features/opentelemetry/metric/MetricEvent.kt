package ai.koog.agents.features.opentelemetry.metric

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import kotlinx.datetime.Instant

internal abstract class MetricEvent {
    abstract val id: String
    abstract val timestamp: Instant
    abstract val metricName: String
    abstract val attributes: List<Attribute>
}

internal class CounterMetricEvent(
    override val id: String,
    override val timestamp: Instant,
    override val metricName: String,
    override val attributes: List<Attribute>,
    val value: Long
) : MetricEvent() {

    fun copy(
        metricName: String? = null,
        attributes: List<Attribute>? = null,
        value: Long? = null
    ): CounterMetricEvent {
        return CounterMetricEvent(
            this.id,
            this.timestamp,
            metricName ?: this.metricName,
            attributes ?: this.attributes,
            value ?: this.value
        )
    }
}

internal class HistogramMetricEvent(
    override val id: String,
    override val timestamp: Instant,
    override val metricName: String,
    override val attributes: List<Attribute>,
    val value: Double
) : MetricEvent() {

    fun copy(
        metricName: String? = null,
        attributes: List<Attribute>? = null,
        value: Double? = null
    ): HistogramMetricEvent {
        return HistogramMetricEvent(
            this.id,
            this.timestamp,
            metricName ?: this.metricName,
            attributes ?: this.attributes,
            value ?: this.value
        )
    }
}
