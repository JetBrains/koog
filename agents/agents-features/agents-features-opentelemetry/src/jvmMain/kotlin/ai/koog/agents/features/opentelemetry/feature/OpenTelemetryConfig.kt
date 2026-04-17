package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.handler.AgentLifecycleEventContext
import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
import ai.koog.agents.features.opentelemetry.attribute.addAttributes
import ai.koog.agents.features.opentelemetry.integration.SpanAdapter
import ai.koog.agents.features.opentelemetry.integration.datadog.addDatadogExporterImpl
import ai.koog.agents.features.opentelemetry.integration.langfuse.addLangfuseExporterImpl
import ai.koog.agents.features.opentelemetry.integration.weave.addWeaveExporterImpl
import ai.koog.agents.features.opentelemetry.metric.MetricFilter
import ai.koog.agents.features.opentelemetry.metric.adapter.MetricAdapter
import ai.koog.utils.time.toJavaDuration
import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.exporter.logging.LoggingMetricExporter
import io.opentelemetry.exporter.logging.LoggingSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.InstrumentSelector
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder
import io.opentelemetry.sdk.metrics.View
import io.opentelemetry.sdk.metrics.export.MetricExporter
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import io.opentelemetry.sdk.trace.samplers.Sampler
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Properties
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration
import java.time.Duration as JavaDuration

/**
 * Configuration class for OpenTelemetry integration.
 *
 * Provides seamless integration with the OpenTelemetry SDK, allowing initialization
 * and customization of various components such as the tracer, meter, exporters, etc.
 */
public class OpenTelemetryConfig : FeatureConfig() {

    private companion object {

        private val logger = KotlinLogging.logger { }

        private val osName = System.getProperty("os.name")

        private val osVersion = System.getProperty("os.version")

        private val osArch = System.getProperty("os.arch")

        /**
         * The default interval for metric reading, which can be overridden when adding a custom exporter.
         */
        val DEFAULT_METER_INTERVAL: Duration = 1.seconds
    }

    private val productProperties = run {
        val props = Properties()
        this::class.java.classLoader.getResourceAsStream("product.properties")?.use { stream ->
            props.load(stream)
        }
        props
    }

    private val customSpanExporters = mutableListOf<SpanExporter>()

    private val customSpanProcessorsCreator = mutableListOf<(SpanExporter) -> SpanProcessor>()

    private val customResourceAttributes = mutableMapOf<AttributeKey<*>, Any>()

    private val customMetricExporters = mutableListOf<Pair<MetricExporter, Duration>>()

    private var _sdk: OpenTelemetrySdk? = null

    private var _serviceName: String = productProperties.getProperty("name") ?: "ai.koog"

    private var _serviceVersion: String = productProperties.getProperty("version") ?: "0.0.0"

    private var _instrumentationScopeName: String = _serviceName

    private var _instrumentationScopeVersion: String = _serviceVersion

    private var _sampler: Sampler? = null

    private var _verbose: Boolean = false

    private var _spanAdapter: SpanAdapter? = null

    private var _shutdownOnAgentClose: Boolean = false

    private var _metricAdapter: MetricAdapter? = null

    private val _metricFilters = mutableListOf<MetricFilter>()

    override fun setEventFilter(filter: (AgentLifecycleEventContext) -> Boolean) {
        // Do not allow events filtering for the OpenTelemetry feature
        // OpenTelemetry relay on the hierarchy. Filtering events can break the feature logic.
        throw UnsupportedOperationException("Events filtering is not allowed for the OpenTelemetry feature.")
    }

    /**
     * Indicates whether verbose telemetry data is enabled.
     *
     * When this value is `true`, the application collects more detailed telemetry data.
     * This setting is useful for debugging and detailed monitoring but may result in
     * increased resource usage or performance overhead.
     *
     * The value reflects the setting controlled through the `setVerbose(verbose: Boolean)` method,
     * with a default value of `false` if not explicitly configured.
     */
    public val isVerbose: Boolean
        get() = _verbose

    /**
     * Provides an instance of the `OpenTelemetrySdk`.
     *
     * This property retrieves the existing instance of the SDK if it has already been initialized. If the SDK has not
     * been initialized, it initializes a new instance with the specified service name and service version.
     * The initialized SDK instance is cached for future access.
     *
     * The `initializeOpenTelemetry` function configures the SDK with the appropriate service attributes, trace
     * providers, span processors, and exporters.
     *
     * @return The initialized or previously cached `OpenTelemetrySdk`.
     */
    public val sdk: OpenTelemetrySdk
        get() {
            return _sdk ?: initializeOpenTelemetry().also { sdk ->
                _sdk = sdk

                // Set the instrumentation scope name only once when SDK is created
                _instrumentationScopeName = _serviceName
                _instrumentationScopeVersion = _serviceVersion
            }
        }

    /**
     * Provides access to the `Tracer` instance for tracking and recording tracing data.
     */
    public val tracer: Tracer
        get() = sdk.getTracer(_instrumentationScopeName, _instrumentationScopeVersion)

    /**
     * The `Meter` can be utilized to create metric instruments such as counters, histograms, and gauges,
     * which can then be used to track application-specific metrics.
     */
    public val meter: Meter
        get() = sdk.getMeter(_instrumentationScopeName)

    /**
     * Adds a MetricExporter to the OpenTelemetry configuration.
     * This exporter will be used to export metrics collected during the application's execution.
     *
     * @param exporter The MetricExporter instance to be added to the list of custom metric exporters.
     * @param meterInterval The interval between metric reads. Defaults to [DEFAULT_METER_INTERVAL].
     */
    @JavaAPI
    @JvmOverloads
    public fun addMetricExporter(exporter: MetricExporter, meterInterval: Duration = DEFAULT_METER_INTERVAL) {
        customMetricExporters.add(exporter to meterInterval)
    }

    /**
     * Java-friendly overload of [addMetricExporter] that accepts a [JavaDuration] interval.
     */
    @JavaAPI
    public fun addMetricExporter(exporter: MetricExporter, meterInterval: JavaDuration) {
        customMetricExporters.add(exporter to meterInterval.toKotlinDuration())
    }

    /**
     * Adds a metric filter to the OpenTelemetry configuration. This filter is used to specify
     * which attribute keys should be retained for a specific metric during telemetry data processing.
     *
     * @param metricName The name of the metric to which the filter will be applied.
     * @param keysToRetain A set of attribute keys that should be retained for the specified metric.
     */
    @JavaAPI
    public fun addMetricFilter(metricName: String, keysToRetain: Set<String>) {
        _metricFilters.add(MetricFilter(metricName, keysToRetain))
    }

    /**
     * Adds a custom metric adapter to the OpenTelemetry configuration.
     * The adapter can be used to process or modify metric events during telemetry data handling.
     *
     * @param adapter The MetricAdapter implementation that will handle
     *                processing of metric events.
     */
    internal fun addMetricAdapter(adapter: MetricAdapter) {
        _metricAdapter = adapter
    }

    /**
     * The name of the service associated with this OpenTelemetry configuration.
     */
    public val serviceName: String
        get() = _serviceName

    /**
     * The version of the service used in the OpenTelemetry configuration.
     */
    public val serviceVersion: String
        get() = _serviceVersion

    /**
     * Indicates whether the OpenTelemetry SDK should be automatically closed when the agent closes.
     *
     * Defaults to `false`. When set to `true` via [setShutdownOnAgentClose], the SDK will be closed
     * (flushing all pending spans) during the agent close lifecycle event.
     *
     * Note: if multiple agents share the same [OpenTelemetryConfig], enabling this will cause
     * the SDK to be closed when the first agent closes, which may affect the following agents.
     */
    public val isShutdownOnAgentClose: Boolean
        get() = _shutdownOnAgentClose

    /**
     * A property providing access to the current instance of the [SpanAdapter].
     * This is backed by a private field [_spanAdapter].
     * Used to handle span-related operations between components.
     */
    internal val spanAdapter: SpanAdapter?
        get() = _spanAdapter

    internal val metricAdapter: MetricAdapter?
        get() = _metricAdapter

    /**
     * Sets the service information for the OpenTelemetry configuration.
     * This information is used to identify the service in telemetry data.
     *
     * @param serviceName The name of the service.
     * @param serviceVersion The version of the service.
     */
    public fun setServiceInfo(serviceName: String, serviceVersion: String) {
        _serviceName = serviceName
        _serviceVersion = serviceVersion
    }

    /**
     * Adds a SpanExporter to the OpenTelemetry configuration. This exporter will
     * be used to export spans collected during the application's execution.
     *
     * @param exporter The SpanExporter instance to be added to the list of custom span exporters.
     */
    public fun addSpanExporter(exporter: SpanExporter) {
        customSpanExporters.add(exporter)
    }

    /**
     * Adds a [SpanProcessor] creator function to the OpenTelemetry configuration.
     *
     * @param processor A function that takes a SpanExporter and returns the [SpanProcessor].
     *                        This allows defining custom logic for processing spans before they are exported.
     */
    public fun addSpanProcessor(processor: (SpanExporter) -> SpanProcessor) {
        customSpanProcessorsCreator.add(processor)
    }

    /**
     * Adds resource attributes to the OpenTelemetry configuration.
     * Resource attributes are key-value pairs that provide metadata
     * describing the entity producing telemetry data.
     *
     * @param attributes A map where the keys are of type [AttributeKey]
     *                   and the values are of type T. These attributes
     *                   will be added to the resource.
     * @param T The type of the values in the attribute map, which must be non-null.
     */
    public fun <T> addResourceAttributes(attributes: Map<AttributeKey<T>, T>) where T : Any {
        customResourceAttributes.putAll(attributes)
    }

    /**
     * Sets the sampler to be used by the OpenTelemetry configuration.
     * The sampler determines which spans are sampled and exported during application execution.
     *
     * @param sampler The sampler instance to set for the OpenTelemetry configuration.
     */
    public fun setSampler(sampler: Sampler) {
        _sampler = sampler
    }

    /**
     * Controls whether verbose telemetry data should be captured during application execution.
     * When set to `true`, the application collects more detailed telemetry data.
     * This option can be useful for debugging and fine-grained monitoring but may impact performance.
     *
     * Default value is `false`, meaning verbose data capture is disabled.
     */
    public fun setVerbose(verbose: Boolean) {
        _verbose = verbose
    }

    /**
     *  Manually sets the [OpenTelemetrySdk] instance.
     *
     * This method allows injection of a pre-configured [OpenTelemetrySdk].
     * When the SDK is set through this method, it also updates the instrumentation scope name and version
     * based on the current service information.
     *
     * > Note: When using this method, any custom configuration applied via
     * > [addSpanExporter], [addSpanProcessor], [addResourceAttributes] or [setSampler]
     * > will be ignored, since the provided SDK is assumed to be fully configured.
     *
     * @param sdk The [OpenTelemetrySdk] instance to use for OpenTelemetry configuration.
     */
    public fun setSdk(sdk: OpenTelemetrySdk) {
        _sdk = sdk
    }

    /**
     * Sets whether the OpenTelemetry SDK should be automatically closed when the agent closes.
     *
     * When enabled, [OpenTelemetrySdk.close] is called during the agent closing lifecycle event,
     * which flushes all pending spans to exporters and shuts down the SDK.
     *
     * Defaults to `false`. For manual SDK lifecycle management, call [sdk]`.close()` directly.
     *
     * @param shutdownOnAgentClose `true` to close the SDK when the agent closes, `false` to leave
     *        SDK lifecycle management to the caller.
     */
    public fun setShutdownOnAgentClose(shutdownOnAgentClose: Boolean) {
        _shutdownOnAgentClose = shutdownOnAgentClose
    }

    /**
     * Adds a custom span adapter for post-processing GenAI agent spans.
     * The adapter can modify span data, add attributes/events, or perform other
     * post-processing logic before spans are completed.
     *
     * @param adapter The ProcessSpanAdapter implementation that will handle
     *                post-processing of GenAI agent spans
     */
    internal fun addSpanAdapter(adapter: SpanAdapter) {
        _spanAdapter = adapter
    }

    //region Private Methods

    private fun initializeOpenTelemetry(): OpenTelemetrySdk {
        // SDK
        val builder = OpenTelemetrySdk.builder()

        // Tracing
        val sampler = createSampler()
        val resource = createResources()
        val exporters: List<SpanExporter> = createSpanExporters()

        val traceProviderBuilder = SdkTracerProvider.builder()
            .setSampler(sampler)
            .setResource(resource)

        exporters.forEach { exporter: SpanExporter ->
            traceProviderBuilder.addProcessors(exporter)
        }

        val metricProvider = SdkMeterProvider.builder()
            .setResource(resource)

        val metricExporters = createMetricExporters()

        metricExporters.forEach { (exporter, meterInterval) ->
            val reader = PeriodicMetricReader
                .builder(exporter)
                .setInterval(meterInterval.toJavaDuration())
                .build()

            metricProvider.registerMetricReader(reader)
        }

        _metricFilters.forEach { filter -> metricProvider.registerView(filter) }

        val sdk = builder
            .setTracerProvider(traceProviderBuilder.build())
            .setMeterProvider(metricProvider.build())
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .build()

        return sdk
    }

    private fun createSampler(): Sampler {
        return _sampler ?: Sampler.alwaysOn()
    }

    private fun createResources(): Resource {
        val defaultResourceAttributes: Map<AttributeKey<*>, String> = buildMap {
            put(AttributeKey.stringKey("service.name"), _serviceName)
            put(AttributeKey.stringKey("service.version"), _serviceVersion)
            put(AttributeKey.stringKey("service.instance.time"), DateTimeFormatter.ISO_INSTANT.format(Instant.now()))

            osName?.let { osName -> put(AttributeKey.stringKey("os.type"), osName) }
            osVersion?.let { osVersion -> put(AttributeKey.stringKey("os.version"), osVersion) }
            osArch?.let { osArch -> put(AttributeKey.stringKey("os.arch"), osArch) }
        }

        val resourceAttributes = Attributes.builder()
            .addAttributes(defaultResourceAttributes)
            .addAttributes(customResourceAttributes)
            .build()

        val resource = Resource.create(resourceAttributes)
        return resource
    }

    private fun createSpanExporters(): List<SpanExporter> = buildList {
        if (customSpanExporters.isEmpty()) {
            logger.debug { "No custom span exporters configured. Use log span exporter by default." }
            add(LoggingSpanExporter.create())
        }

        customSpanExporters.forEach { exporter ->
            logger.debug { "Adding span exporter: ${exporter::class.simpleName}" }
            add(exporter)
        }
    }

    private fun createMetricExporters(): List<Pair<MetricExporter, Duration>> = buildList {
        if (customMetricExporters.isEmpty()) {
            logger.debug { "No custom metric exporters configured. Use log metric exporter by default." }
            add(LoggingMetricExporter.create() to DEFAULT_METER_INTERVAL)
        }

        customMetricExporters.forEach { (exporter, interval) ->
            logger.debug { "Adding metric exporter: ${exporter::class.simpleName}" }
            add(exporter to interval)
        }
    }

    private fun SdkTracerProviderBuilder.addProcessors(exporter: SpanExporter) {
        if (customSpanProcessorsCreator.isEmpty()) {
            logger.debug {
                "No custom span processors configured. Use batch span processor with ${exporter::class.simpleName} as an exporter."
            }
            addSpanProcessor(SimpleSpanProcessor.builder(exporter).build())
            return
        }

        customSpanProcessorsCreator.forEach { processorCreator ->
            val spanProcessor = processorCreator(exporter)
            logger.debug { "Adding span processor: ${spanProcessor::class.simpleName}" }
            addSpanProcessor(spanProcessor)
        }
    }

    private fun SdkMeterProviderBuilder.registerView(filter: MetricFilter) {
        val selector = InstrumentSelector.builder().setName(filter.metricName).build()
        val view = View.builder().setAttributeFilter(filter.attributesKeysToRetain).build()

        this.registerView(selector, view)
    }

    //endregion Private Methods

    // integrations:

    /**
     * Configure an OpenTelemetry span exporter that sends data to [Langfuse](https://langfuse.com/).
     *
     * @param langfuseUrl the base URL of the Langfuse instance.
     *        If not a set is retrieved from `LANGFUSE_HOST` environment variable.
     *        Defaults to [https://cloud.langfuse.com](https://cloud.langfuse.com).
     * @param langfusePublicKey if not set is retrieved from `LANGFUSE_PUBLIC_KEY` environment variable.
     * @param langfuseSecretKey if not set is retrieved from `LANGFUSE_SECRET_KEY` environment variable.
     * @param timeout OpenTelemetry SpanExporter timeout.
     *        See [io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporterBuilder.setTimeout].
     * @param traceAttributes list of trace-level Langfuse attributes.
     *        See the full list: [Trace-Level Attributes](https://langfuse.com/integrations/native/opentelemetry#trace-level-attributes)
     *
     * @see <a href="https://langfuse.com/docs/get-started#create-new-project-in-langfuse">How to create a new project in Langfuse</a>
     * @see <a href="https://langfuse.com/faq/all/where-are-langfuse-api-keys">How to set up API keys in Langfuse</a>
     * @see <a href="https://langfuse.com/docs/opentelemetry/get-started#opentelemetry-endpoint">Langfuse OpenTelemetry Docs</a>
     */
    @JavaAPI
    @JvmOverloads
    public fun addLangfuseExporter(
        langfuseUrl: String? = null,
        langfusePublicKey: String? = null,
        langfuseSecretKey: String? = null,
        timeout: JavaDuration? = null,
        traceAttributes: List<CustomAttribute>? = null
    ): Unit = this.addLangfuseExporterImpl(
        langfuseUrl,
        langfusePublicKey,
        langfuseSecretKey,
        timeout?.toKotlinDuration(),
        traceAttributes
    )

    /**
     * Configure an OpenTelemetry span exporter that sends data to [W&B Weave](https://wandb.ai/site/weave/).
     *
     * @param weaveOtelBaseUrl the URL of the Weave OpenTelemetry endpoint.
     *        If not set is retrieved from `WEAVE_URL` environment variable.
     *        Defaults to [https://trace.wandb.ai](https://trace.wandb.ai).
     * @param weaveEntity can be found by visiting your W&B dashboard at [https://wandb.ai/home](https://wandb.ai/home) and
     *        checking the *Teams* field in the left sidebar.
     *        If not set is retrieved from `WEAVE_ENTITY` environment variable.
     * @param weaveProjectName name of your Weave project.
     *        If not set is retrieved from `WEAVE_PROJECT_NAME` environment variable.
     * @param weaveApiKey can be created on the [https://wandb.ai/authorize](https://wandb.ai/authorize) page.
     *        If not set is retrieved from `WEAVE_API_KEY` environment variable.
     * @param timeout OpenTelemetry SpanExporter timeout.
     *        See [io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporterBuilder.setTimeout].
     *
     * @see <a href="https://weave-docs.wandb.ai/guides/tracking/otel/">Weave OpenTelemetry Docs</a>
     */
    @JavaAPI
    @JvmOverloads
    public fun addWeaveExporter(
        weaveOtelBaseUrl: String? = null,
        weaveEntity: String? = null,
        weaveProjectName: String? = null,
        weaveApiKey: String? = null,
        timeout: JavaDuration? = null,
    ): Unit = addWeaveExporterImpl(
        weaveOtelBaseUrl,
        weaveEntity,
        weaveProjectName,
        weaveApiKey,
        timeout?.toKotlinDuration()
    )

    /**
     * Configure an OpenTelemetry span exporter that sends data to [Datadog](https://www.datadoghq.com/)
     * via direct OTLP intake.
     *
     * @param datadogApiKey Datadog API key.
     *        If not set, is retrieved from `DD_API_KEY` environment variable.
     * @param datadogSite Datadog site (e.g. `datadoghq.com`, `datadoghq.eu`).
     *        If not set, is retrieved from `DD_SITE` environment variable.
     *        Defaults to `datadoghq.com`.
     * @param timeout OpenTelemetry SpanExporter timeout.
     *        See [io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporterBuilder.setTimeout].
     * @param traceAttributes resource-level attributes to add to all exported spans.
     *        Useful for tagging traces with application-specific metadata
     *        (e.g. `"env"`, `"tenant_id"`, `"prompt_name"`).
     *
     * @see <a href="https://docs.datadoghq.com/opentelemetry/guide/otlp_api/">Datadog OTLP API Intake</a>
     * @see <a href="https://docs.datadoghq.com/llm_observability/">Datadog LLM Observability</a>
     */
    @JavaAPI
    @JvmOverloads
    public fun addDatadogExporter(
        datadogApiKey: String? = null,
        datadogSite: String? = null,
        timeout: JavaDuration? = null,
        traceAttributes: Map<String, String>? = null,
    ): Unit = addDatadogExporterImpl(
        datadogApiKey,
        datadogSite,
        timeout?.toKotlinDuration(),
        traceAttributes
    )
}
