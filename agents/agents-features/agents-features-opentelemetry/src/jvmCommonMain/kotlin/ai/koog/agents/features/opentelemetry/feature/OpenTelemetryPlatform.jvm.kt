package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.features.opentelemetry.metric.MetricFilter
import ai.koog.agents.features.opentelemetry.metric.adapter.MetricAdapter
import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.export.MetricExporter
import kotlin.time.Duration

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
internal actual class OpenTelemetryPlatform actual constructor() {

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    /**
     * Java-SDK metric exporters with their read interval, wired into the lazy [SdkMeterProvider].
     */
    val metricExporters: MutableList<Pair<MetricExporter, Duration>> = mutableListOf()

    /**
     * Per-metric attribute-retention filters applied when building the [SdkMeterProvider].
     */
    val metricFilters: MutableList<MetricFilter> = mutableListOf()

    /**
     * Optional adapter for post-processing metric events before they reach the SDK.
     */
    var metricAdapter: MetricAdapter? = null

    /**
     * Lazily initialized Java-SDK meter provider. Built on first access to [OpenTelemetryConfig.meter]
     * so callers that never touch metrics never pay the SDK construction cost.
     */
    @Volatile
    var meterProvider: SdkMeterProvider? = null

    actual fun shutdown() {
        meterProvider?.let { provider ->
            runCatching {
                provider.close()
            }.onFailure {
                logger.warn(it) { "Failed to close SdkMeterProvider" }
            }
        }
    }
}
