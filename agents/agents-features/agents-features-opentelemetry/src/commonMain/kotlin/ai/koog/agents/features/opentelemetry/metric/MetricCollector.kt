package ai.koog.agents.features.opentelemetry.metric

import ai.koog.agents.features.opentelemetry.feature.OpenTelemetryConfig

/**
 * Records agent telemetry as OpenTelemetry metric events.
 *
 * The interface lives in `commonMain`; the real implementation is JVM-only because the Kotlin
 * Multiplatform OpenTelemetry SDK 0.3.0 ships no metrics API. On non-JVM targets the
 * [createMetricCollector] factory returns [NoOpMetricCollector] so the feature's pipeline
 * handlers can call it unconditionally.
 */
internal interface MetricCollector {
    fun storeMetricEvent(metricEvent: MetricEvent<*>)
    fun getMetricEvent(id: String): MetricEvent<*>?
    fun addCounterMetricEvent(metricEvent: CounterMetricEvent)
    fun recordHistogramMetricEvent(metricEvent: HistogramMetricEvent)
    fun flushPendingAsErrors(error: Throwable?)
}

internal object NoOpMetricCollector : MetricCollector {
    override fun storeMetricEvent(metricEvent: MetricEvent<*>) {}
    override fun getMetricEvent(id: String): MetricEvent<*>? = null
    override fun addCounterMetricEvent(metricEvent: CounterMetricEvent) {}
    override fun recordHistogramMetricEvent(metricEvent: HistogramMetricEvent) {}
    override fun flushPendingAsErrors(error: Throwable?) {}
}

/**
 * Platform-specific factory for [MetricCollector].
 *
 * JVM returns an implementation backed by the Java OpenTelemetry SDK (`Meter`, `LongCounter`,
 * `DoubleHistogram`). Non-JVM returns [NoOpMetricCollector].
 */
internal expect fun createMetricCollector(config: OpenTelemetryConfig): MetricCollector
