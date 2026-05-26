package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.handler.AgentLifecycleEventContext
import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
import ai.koog.agents.features.opentelemetry.integration.SpanAdapter
import ai.koog.agents.features.opentelemetry.integration.datadog.addDatadogExporter
import ai.koog.agents.features.opentelemetry.integration.langfuse.addLangfuseExporter
import ai.koog.agents.features.opentelemetry.integration.weave.addWeaveExporter
import ai.koog.agents.features.opentelemetry.metric.MetricFilter
import ai.koog.agents.features.opentelemetry.metric.adapter.MetricAdapter
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.kotlin.factory.ContextFactory
import io.opentelemetry.kotlin.tracing.export.toOtelKotlinSpanExporter
import io.opentelemetry.sdk.metrics.InstrumentSelector
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder
import io.opentelemetry.sdk.metrics.View
import io.opentelemetry.sdk.metrics.export.MetricExporter
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.export.SpanExporter
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlin.time.toKotlinDuration
import java.time.Duration as JavaDuration

/**
 * Configuration class for the OpenTelemetry feature, providing integration and export mechanisms
 * for traces, metrics, and other telemetry data. This class abstracts the underlying implementation
 * and offers convenient methods for setup and configuration within the Kotlin Multiplatform SDK.
 *
 * The class includes constructors, methods, and properties used to manage telemetry exporters, filters,
 * and adapters, as well as Java-friendly API overrides for compatibility with Java callers.
 *
 * This configuration is designed to work across different platforms and integrates seamlessly with Java SDK.
 *
 * Note: Some configurations rely on the Java SDK due to limitations in the current Kotlin Multiplatform SDK.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public actual class OpenTelemetryConfig internal actual constructor(
    internal actual val delegate: OpenTelemetryConfigImpl,
) : FeatureConfig(), OpenTelemetryConfigAPI by delegate {

    public actual constructor() : this(delegate = OpenTelemetryConfigImpl())

    internal actual val platform: OpenTelemetryPlatform
        get() = delegate.platform

    internal actual val contextFactory: ContextFactory
        get() = delegate.contextFactory

    internal actual val spanAdapter: SpanAdapter?
        get() = delegate.spanAdapter

    internal actual val instrumentationScopeName: String
        get() = delegate.instrumentationScopeName

    internal actual fun buildResourceMap(): Map<String, Any> = delegate.buildResourceMap()

    internal actual suspend fun closeSdks() {
        delegate.closeSdks()
    }

    /**
     * Java-friendly 2-arg overload of [setServiceInfo] that omits the optional `serviceNamespace`.
     */
    public fun setServiceInfo(serviceName: String, serviceVersion: String) {
        delegate.setServiceInfo(serviceName, serviceVersion, null)
    }

    override fun setEventFilter(filter: (AgentLifecycleEventContext) -> Boolean) {
        // Do not allow events filtering for the OpenTelemetry feature:
        // OpenTelemetry relies on the lifecycle hierarchy and dropped events would corrupt spans.
        throw UnsupportedOperationException("Events filtering is not allowed for the OpenTelemetry feature.")
    }

    /**
     * Default interval for metric readings when [addMetricExporter] is called without one.
     */
    private val defaultMeterInterval: Duration = 1.seconds

    /**
     * Java-SDK [Meter] used to create counters, histograms, and gauges.
     */
    public val meter: Meter
        get() {
            val provider = platform.meterProvider ?: initializeMeterProvider().also { platform.meterProvider = it }
            return provider.get(instrumentationScopeName)
        }

    //region Span Exporters

    /**
     * JVM convenience that bridges a Java-SDK [SpanExporter] to the Kotlin SDK via [toOtelKotlinSpanExporter] and
     * registers it through [addSpanExporter][OpenTelemetryConfig.addSpanExporter].
     *
     * For full control over the processor (custom batching parameters, simple/composite processors)
     * use `addSpanProcessor { batchSpanProcessor(exporter.toOtelKotlinSpanExporter()) }` directly.
     */
    public fun addSpanExporter(exporter: SpanExporter) {
        addSpanExporter(exporter.toOtelKotlinSpanExporter())
    }

    //endregion Span Exporters

    //region Integration Exporters

    /**
     * Java-friendly entry point for the Langfuse integration. Mirrors the commonMain
     * [addLangfuseExporter][ai.koog.agents.features.opentelemetry.integration.langfuse.addLangfuseExporter]
     * but accepts [java.time.Duration] for [timeout] so Java callers can pass every parameter -
     * the commonMain overload uses [kotlin.time.Duration], whose value-class JVM-name mangling makes
     * the `traceAttributes` argument unreachable from Java.
     *
     * @see ai.koog.agents.features.opentelemetry.integration.langfuse.addLangfuseExporter
     */
    @JavaAPI
    @JvmOverloads
    public fun addLangfuseExporter(
        langfuseUrl: String? = null,
        langfusePublicKey: String? = null,
        langfuseSecretKey: String? = null,
        timeout: JavaDuration? = null,
        traceAttributes: List<CustomAttribute>? = null,
    ) {
        addLangfuseExporter(
            langfuseUrl = langfuseUrl,
            langfusePublicKey = langfusePublicKey,
            langfuseSecretKey = langfuseSecretKey,
            timeout = timeout?.toKotlinDuration(),
            traceAttributes = traceAttributes,
        )
    }

    /**
     * Java-friendly entry point for the W&B Weave integration. Mirrors the commonMain
     * [addWeaveExporter][ai.koog.agents.features.opentelemetry.integration.weave.addWeaveExporter]
     * but accepts [java.time.Duration] for [timeout] - the commonMain overload uses
     * [kotlin.time.Duration], whose value-class JVM-name mangling hides the timeout overload from
     * Java callers.
     *
     * @see ai.koog.agents.features.opentelemetry.integration.weave.addWeaveExporter
     */
    @JavaAPI
    @JvmOverloads
    public fun addWeaveExporter(
        weaveOtelBaseUrl: String? = null,
        weaveEntity: String? = null,
        weaveProjectName: String? = null,
        weaveApiKey: String? = null,
        timeout: JavaDuration? = null,
    ) {
        addWeaveExporter(
            weaveOtelBaseUrl = weaveOtelBaseUrl,
            weaveEntity = weaveEntity,
            weaveProjectName = weaveProjectName,
            weaveApiKey = weaveApiKey,
            timeout = timeout?.toKotlinDuration(),
        )
    }

    /**
     * Java-friendly entry point for the Datadog integration. Mirrors the commonMain
     * [addDatadogExporter][ai.koog.agents.features.opentelemetry.integration.datadog.addDatadogExporter]
     * but accepts [java.time.Duration] for [timeout] so Java callers can pass every parameter -
     * the commonMain overload uses [kotlin.time.Duration], whose value-class JVM-name mangling makes
     * the `resourceAttributes` argument unreachable from Java.
     *
     * @see ai.koog.agents.features.opentelemetry.integration.datadog.addDatadogExporter
     */
    @JavaAPI
    @JvmOverloads
    public fun addDatadogExporter(
        datadogApiKey: String? = null,
        url: String? = null,
        timeout: JavaDuration? = null,
        resourceAttributes: Map<String, String>? = null,
    ) {
        addDatadogExporter(
            datadogApiKey = datadogApiKey,
            url = url,
            timeout = timeout?.toKotlinDuration(),
            resourceAttributes = resourceAttributes,
        )
    }

    //endregion Integration Exporters

    //region Metrics

    /**
     * Registers a Java-SDK [MetricExporter] driven by a [PeriodicMetricReader] at [meterInterval].
     *
     * Note: the Kotlin Multiplatform OpenTelemetry SDK 0.3.0 does not include metrics.
     *       Use metrics from the Java SDK.
     */
    public fun addMetricExporter(exporter: MetricExporter, meterInterval: Duration = defaultMeterInterval) {
        platform.metricExporters.add(exporter to meterInterval)
    }

    /**
     * Registers a OTel Java SDK [MetricExporter] driven by a [PeriodicMetricReader] at [meterInterval].
     */
    @JavaAPI
    public fun addMetricExporter(exporter: MetricExporter, meterInterval: JavaDuration) {
        platform.metricExporters.add(exporter to meterInterval.toKotlinDuration())
    }

    /**
     * Registers a filter that restricts the attribute keys retained on a given metric during aggregation.
     */
    public fun addMetricFilter(metricName: String, keysToRetain: Set<String>) {
        platform.metricFilters.add(MetricFilter(metricName, keysToRetain))
    }

    /**
     * Installs a [MetricAdapter] used to post-process metric events before they are recorded.
     */
    internal fun addMetricAdapter(adapter: MetricAdapter) {
        platform.metricAdapter = adapter
    }

    //endregion Metrics

    //region Private Methods

    private fun initializeMeterProvider(): SdkMeterProvider {
        val builder: SdkMeterProviderBuilder = SdkMeterProvider.builder()
            .setResource(createJavaResource())

        platform.metricExporters.forEach { (exporter, interval) ->
            val reader = PeriodicMetricReader
                .builder(exporter)
                .setInterval(interval.toJavaDuration())
                .build()
            builder.registerMetricReader(reader)
        }

        platform.metricFilters.forEach { filter ->
            val selector = InstrumentSelector.builder().setName(filter.metricName).build()
            val view = View.builder().setAttributeFilter(filter.attributesKeysToRetain).build()
            builder.registerView(selector, view)
        }

        return builder.build()
    }

    private fun createJavaResource(): Resource {
        val builder = Attributes.builder()
        buildResourceMap().forEach { (key, value) ->
            when (value) {
                is String -> builder.put(AttributeKey.stringKey(key), value)
                is Long -> builder.put(AttributeKey.longKey(key), value)
                is Int -> builder.put(AttributeKey.longKey(key), value.toLong())
                is Double -> builder.put(AttributeKey.doubleKey(key), value)
                is Float -> builder.put(AttributeKey.doubleKey(key), value.toDouble())
                is Boolean -> builder.put(AttributeKey.booleanKey(key), value)
                else -> builder.put(AttributeKey.stringKey(key), value.toString())
            }
        }
        return Resource.create(builder.build())
    }

    //endregion Private Methods
}
