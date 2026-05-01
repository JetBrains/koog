package ai.koog.agents.features.opentelemetry.metric

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import ai.koog.utils.time.KoogClock
import kotlin.time.Duration
import kotlin.time.Instant

internal sealed interface MetricEvent<T : MetricEvent<T>> {
    val id: String
    val timestamp: Instant
    val metricName: String
    val attributes: List<Attribute>

    fun withAttributes(attributes: List<Attribute>): T
}

internal fun MetricEvent<*>.duration(): Duration = KoogClock.System.now() - timestamp

internal open class BaseMetricEvent(
    override val id: String,
    override val timestamp: Instant,
    override val metricName: String,
    override val attributes: List<Attribute>
) : MetricEvent<BaseMetricEvent> {
    override fun withAttributes(attributes: List<Attribute>): BaseMetricEvent {
        return BaseMetricEvent(id, timestamp, metricName, attributes)
    }
}

internal data class CounterMetricEvent(
    override val id: String,
    override val timestamp: Instant,
    override val metricName: String,
    override val attributes: List<Attribute>,
    val value: Long
) : MetricEvent<CounterMetricEvent> {
    override fun withAttributes(attributes: List<Attribute>): CounterMetricEvent {
        return CounterMetricEvent(id, timestamp, metricName, attributes, value)
    }
}

internal data class HistogramMetricEvent(
    override val id: String,
    override val timestamp: Instant,
    override val metricName: String,
    override val attributes: List<Attribute>,
    val value: Double
) : MetricEvent<HistogramMetricEvent> {
    override fun withAttributes(attributes: List<Attribute>): HistogramMetricEvent {
        return HistogramMetricEvent(id, timestamp, metricName, attributes, value)
    }
}
