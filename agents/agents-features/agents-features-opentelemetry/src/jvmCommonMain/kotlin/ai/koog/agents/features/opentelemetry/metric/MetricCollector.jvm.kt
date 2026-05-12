package ai.koog.agents.features.opentelemetry.metric

import ai.koog.agents.features.opentelemetry.attribute.toJavaSdkAttributes
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetryConfig
import ai.koog.agents.features.opentelemetry.metric.events.toFailedDurationHistogramMetricEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.api.metrics.DoubleHistogram
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.Meter
import java.util.concurrent.ConcurrentHashMap

internal actual fun createMetricCollector(config: OpenTelemetryConfig): MetricCollector =
    JvmMetricCollector(config.meter, config)

internal class JvmMetricCollector(
    private val meter: Meter,
    private val config: OpenTelemetryConfig,
) : MetricCollector {

    private val counters = ConcurrentHashMap<String, LongCounter>()
    private val histograms = ConcurrentHashMap<String, DoubleHistogram>()

    private val metricEvents = ConcurrentHashMap<String, MetricEvent<*>>()

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    init {
        MetricFactory.createTokenUsageHistogramMetric().let {
            histograms[it.name] = addHistogramMetric(it)
        }

        MetricFactory.createToolCallCounterMetric().let {
            counters[it.name] = addCounterMetric(it)
        }

        MetricFactory.createOperationDurationHistogramMetric().let {
            histograms[it.name] = addHistogramMetric(it)
        }
    }

    private fun addCounterMetric(metric: CounterMetric): LongCounter {
        return meter.counterBuilder(metric.name)
            .setDescription(metric.description)
            .setUnit(metric.unit)
            .build()
            .also { it.add(0) }
    }

    private fun addHistogramMetric(metric: HistogramMetric): DoubleHistogram {
        return meter
            .histogramBuilder(metric.name)
            .setDescription(metric.description)
            .setUnit(metric.unit)
            .setExplicitBucketBoundariesAdvice(metric.boundariesAdvice)
            .build()
    }

    override fun storeMetricEvent(metricEvent: MetricEvent<*>) {
        val result = metricEvents.putIfAbsent(metricEvent.id, metricEvent)

        if (result != null) {
            logger.warn { "Metric event (id: ${metricEvent.id}) is already stored. Unable to store event with the same id." }
        }
    }

    override fun getMetricEvent(id: String): MetricEvent<*>? {
        return metricEvents.remove(id)
    }

    /**
     * Drains still-pending start events and records them as failed duration measurements.
     * Called from agent-failure and agent-closing hooks to cover operations whose completion
     * handler never ran - typically LLM calls canceled or failed beneath the pipeline.
     *
     * Events without pre-populated attributes are dropped, since a single timestamp is not
     * enough to build a semconv-compliant data point.
     */
    override fun flushPendingAsErrors(error: Throwable?) {
        val snapshot = metricEvents.keys.toList()
        snapshot.forEach { id ->
            val event = metricEvents.remove(id) ?: return@forEach
            if (event !is BaseMetricEvent || event.attributes.isEmpty()) {
                // No pre-populated attributes - cannot produce a semconv-compliant data point.
                return@forEach
            }
            val failureEvent = event.toFailedDurationHistogramMetricEvent(error, event.duration())
            recordHistogramMetricEvent(failureEvent)
        }
    }

    override fun addCounterMetricEvent(metricEvent: CounterMetricEvent) {
        val updatedMetricEvent = applyMetricAdapter(metricEvent)

        val metric = counters[updatedMetricEvent.metricName]
        if (metric == null) {
            logger.warn { "Counter metric (name: ${metricEvent.metricName}) not found. Please make sure you register the counter metric before usage." }
            return
        }

        metric.add(
            updatedMetricEvent.value,
            updatedMetricEvent.attributes.toJavaSdkAttributes(verbose = config.isVerbose)
        )
    }

    override fun recordHistogramMetricEvent(metricEvent: HistogramMetricEvent) {
        val updatedMetricEvent = applyMetricAdapter(metricEvent)

        val metric = histograms[updatedMetricEvent.metricName]
        if (metric == null) {
            logger.warn { "Histogram metric (name: ${metricEvent.metricName}) not found. Please make sure you register the histogram metric before usage." }
            return
        }

        metric.record(
            updatedMetricEvent.value,
            updatedMetricEvent.attributes.toJavaSdkAttributes(verbose = config.isVerbose)
        )
    }

    private fun <T : MetricEvent<T>> applyMetricAdapter(metricEvent: T): T {
        val adapter = config.platform.metricAdapter ?: return metricEvent
        return try {
            adapter.process(metricEvent)
        } catch (e: Exception) {
            logger.warn(e) { "Metric adapter failed to process event (id: ${metricEvent.id}). Recording original event." }
            metricEvent
        }
    }
}
