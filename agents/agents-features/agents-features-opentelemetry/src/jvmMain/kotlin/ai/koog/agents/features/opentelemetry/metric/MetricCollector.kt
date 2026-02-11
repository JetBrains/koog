package ai.koog.agents.features.opentelemetry.metric

import ai.koog.agents.features.opentelemetry.attribute.toSdkAttributes
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetryConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.api.metrics.DoubleHistogram
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.Meter
import java.util.concurrent.ConcurrentHashMap

internal class MetricCollector(private val meter: Meter, private val config: OpenTelemetryConfig) {

    private val counters = ConcurrentHashMap<String, LongCounter>()

    private val histograms = ConcurrentHashMap<String, DoubleHistogram>()

    private val metricEvents = ConcurrentHashMap<String, MetricEvent>()

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    init {
        MetricFactory.createTokenCounterMetric().let {
            counters[it.name] = addCounterMetric(it)
        }

        MetricFactory.createToolCallCounterMetric().let {
            counters[it.name] = addCounterMetric(it)
        }

        MetricFactory.createOperationDurationHistogramMetric().let {
            histograms[it.name] = addHistogramMetric(it)
        }
    }

    fun addCounterMetric(metric: CounterMetric): LongCounter {
        val counter = meter.counterBuilder(metric.name)
            .setDescription(metric.description)
            .setUnit(metric.unit)
            .build()
            .also { it.add(0) }

        counters[metric.name] = counter

        return counter
    }

    fun addHistogramMetric(metric: HistogramMetric): DoubleHistogram {
        val counter = meter
            .histogramBuilder(metric.name)
            .setDescription(metric.description)
            .setUnit(metric.unit)
            .setExplicitBucketBoundariesAdvice(metric.boundariesAdvice)
            .build()

        histograms[metric.name] = counter

        return counter
    }

    internal fun storeMetricEvent(metricEvent: MetricEvent) {
        val result = metricEvents.putIfAbsent(metricEvent.id, metricEvent)

        if (result == null) {
            logger.warn { "Metric event (id: ${metricEvent.id}) is already stored. Unable to store event with the same id." }
        }
    }

    internal fun getMetricEvent(id: String): MetricEvent? {
        return metricEvents.remove(id)
    }

    internal fun addCounterMetricEvent(metricEvent: CounterMetricEvent) {
        val updatedMetricEvent = config.metricAdapter?.process(metricEvent) ?: metricEvent

        val metric = counters[updatedMetricEvent.metricName]
        if (metric == null) {
            logger.warn { "Counter metric (name: ${metricEvent.metricName}) not found. Please make sure you register the counter metric before usage." }
            return
        }

        metric.add(
            updatedMetricEvent.value,
            updatedMetricEvent.attributes.toSdkAttributes(verbose = config.isVerbose)
        )
    }

    internal fun recordHistogramMetricEvent(metricEvent: HistogramMetricEvent) {
        val updatedMetricEvent = config.metricAdapter?.process(metricEvent) ?: metricEvent

        val metric = histograms[updatedMetricEvent.metricName]
        if (metric == null) {
            logger.warn { "Histogram metric (name: ${metricEvent.metricName}) not found. Please make sure you register the histogram metric before usage." }
            return
        }

        metric.record(
            updatedMetricEvent.value,
            updatedMetricEvent.attributes.toSdkAttributes(verbose = config.isVerbose)
        )
    }
}
