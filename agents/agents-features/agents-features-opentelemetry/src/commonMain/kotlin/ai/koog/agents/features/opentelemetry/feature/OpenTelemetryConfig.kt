package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.handler.AgentLifecycleEventContext
import ai.koog.agents.features.opentelemetry.integration.SpanAdapter
import ai.koog.agents.features.opentelemetry.platform.PlatformInfo
import ai.koog.agents.features.opentelemetry.platform.loadProductProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.createOpenTelemetry
import io.opentelemetry.kotlin.export.TelemetryCloseable
import io.opentelemetry.kotlin.factory.ContextFactory
import io.opentelemetry.kotlin.init.TraceExportConfigDsl
import io.opentelemetry.kotlin.tracing.Tracer
import io.opentelemetry.kotlin.tracing.export.SpanExporter
import io.opentelemetry.kotlin.tracing.export.SpanProcessor
import io.opentelemetry.kotlin.tracing.export.batchSpanProcessor
import io.opentelemetry.kotlin.tracing.export.compositeSpanProcessor
import io.opentelemetry.kotlin.tracing.export.simpleSpanProcessor
import io.opentelemetry.kotlin.tracing.export.stdoutSpanExporter
import kotlin.jvm.JvmOverloads
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Configuration class for the OpenTelemetry integration.
 *
 * Tracing is backed by the Kotlin Multiplatform OpenTelemetry SDK - the tracing surface defined
 * here (span processors, resource attributes, verbose mode, service info) is available on every
 * target.
 *
 * Tracing-side integrations (Langfuse, W&B Weave, Datadog) ship as commonMain extensions on top
 * of an OTLP/JSON exporter, so they work on every target. The Kotlin SDK ships no metrics module,
 * so metrics still live behind JVM-only extension functions declared in `jvmMain` (`addMetricExporter`,
 * `addMetricFilter`, `meter`).
 *
 * Power users who need full control over the SDK (custom samplers, custom span processors,
 * span limits, etc.) can construct their own [OpenTelemetry] via
 * [io.opentelemetry.kotlin.createOpenTelemetry] and inject it via [setSdk] - in that case the
 * other configuration setters become inert.
 */
public class OpenTelemetryConfig : FeatureConfig() {

    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    private val productProperties = loadProductProperties()

    internal val customSpanProcessorFactories: MutableList<TraceExportConfigDsl.() -> SpanProcessor> = mutableListOf()

    internal val customResourceAttributes: MutableMap<String, Any> = mutableMapOf()

    /**
     * Holds JVM-only state threaded through by extension functions in the jvmMain.
     */
    internal val platform: OpenTelemetryPlatform = OpenTelemetryPlatform()

    private var _sdk: OpenTelemetry? = null

    @OptIn(ExperimentalUuidApi::class)
    private val _instanceId: String = Uuid.random().toString()

    private var _serviceName: String = productProperties["name"] ?: "ai.koog"

    private var _serviceVersion: String = productProperties["version"] ?: "0.0.0"

    private var _serviceNamespace: String? = null

    internal var instrumentationScopeName: String = _serviceName
        private set

    internal var instrumentationScopeVersion: String = _serviceVersion
        private set

    private var _verbose: Boolean = false

    private var _spanAdapter: SpanAdapter? = null

    private var _shutdownOnAgentClose: Boolean = false

    override fun setEventFilter(filter: (AgentLifecycleEventContext) -> Boolean) {
        // Do not allow events filtering for the OpenTelemetry feature:
        // OpenTelemetry relies on the lifecycle hierarchy and dropped events would corrupt spans.
        throw UnsupportedOperationException("Events filtering is not allowed for the OpenTelemetry feature.")
    }

    /**
     * Indicates whether verbose telemetry data is enabled.
     *
     * When `true`, more detailed span attributes and events are recorded (e.g., full message
     * contents instead of placeholders). Useful for debugging; may increase volume significantly.
     */
    public val isVerbose: Boolean
        get() = _verbose

    /**
     * The Kotlin Multiplatform OpenTelemetry SDK [Tracer] used by the feature.
     */
    public val tracer: Tracer
        get() = sdk.tracerProvider.getTracer(instrumentationScopeName, instrumentationScopeVersion)

    /**
     * Kotlin SDK context factory for parent-child span wiring.
     */
    internal val contextFactory: ContextFactory
        get() = sdk.context

    /**
     * Provides a lazily initialized instance of the OpenTelemetry SDK.
     *
     * On first access, the SDK is constructed using the configuration specified
     * in the containing class. This includes service information, registered span
     * processors, and resource attributes.
     */
    internal val sdk: OpenTelemetry
        get() = _sdk ?: initializeOpenTelemetry().also { sdk ->
            _sdk = sdk
            // Freeze instrumentation scope identifiers when the SDK is first built.
            instrumentationScopeName = _serviceName
            instrumentationScopeVersion = _serviceVersion
        }

    /**
     * The service name reported in resource attributes.
     */
    public val serviceName: String
        get() = _serviceName

    /**
     * The service version reported in resource attributes.
     */
    public val serviceVersion: String
        get() = _serviceVersion

    /**
     * The service namespace reported in resource attributes, or `null` if not set.
     */
    public val serviceNamespace: String?
        get() = _serviceNamespace

    /**
     * Indicates whether OpenTelemetry resources should be shut down when the agent closes.
     *
     * Defaults to `false`. When `true`, the feature's agent-close handler calls [closeSdks], which
     * shuts down every registered span processor (which in turn shuts down its wrapped exporter)
     * and on JVM also closes the metrics `SdkMeterProvider`. On non-JVM targets the metrics path
     * is a no-op; only span processors are shut down.
     *
     * Note: if multiple agents share the same [OpenTelemetryConfig], enabling this causes shutdown
     * to happen when the first agent closes, which may affect the remaining agents.
     */
    public val isShutdownOnAgentClose: Boolean
        get() = _shutdownOnAgentClose

    /**
     * The span adapter used to post-process every GenAI span before it is finalized.
     */
    internal val spanAdapter: SpanAdapter?
        get() = _spanAdapter

    /**
     * Sets the service information reported in resource attributes.
     *
     * @param serviceName Value for the `service.name` resource attribute.
     * @param serviceVersion Value for the `service.version` resource attribute.
     * @param serviceNamespace Value for the `service.namespace` resource attribute, or `null` to omit it.
     */
    @JvmOverloads
    public fun setServiceInfo(serviceName: String, serviceVersion: String, serviceNamespace: String? = null) {
        _serviceName = serviceName
        _serviceVersion = serviceVersion
        _serviceNamespace = serviceNamespace
    }

    /**
     * Registers [exporter] wrapped in a [batchSpanProcessor] - the OTel-recommended default for
     * production deployments. Spans are buffered and flushed on a worker so the agent never blocks
     * on network I/O at the span end.
     *
     * For full control over the processor (custom batching parameters, custom processor
     * implementations) use [addSpanProcessor] instead.
     *
     * For Java SDK exporters, wrap with `toOtelKotlinSpanExporter()` from the compat package first.
     *
     * @param exporter The Kotlin OTel SDK [SpanExporter] to register.
     */
    public fun addSpanExporter(exporter: SpanExporter) {
        addSpanProcessor { batchSpanProcessor(exporter) }
    }

    /**
     * Registers a [SpanProcessor] produced by a [factory] invoked inside the SDK's
     * [TraceExportConfigDsl] scope. Use this when the convenience [addSpanExporter] (which always
     * wraps in [batchSpanProcessor]) doesn't fit - e.g., custom batching parameters, custom
     * processor implementations, or a [compositeSpanProcessor] over several inner processors.
     *
     * Example:
     * ```
     * addSpanProcessor { batchSpanProcessor(myOtlpExporter, scheduleDelayMs = 500) }
     * addSpanProcessor { compositeSpanProcessor(p1, p2) }
     * ```
     *
     * For Java SDK exporters, wrap with `toOtelKotlinSpanExporter()` from the compat package first.
     *
     * @param factory Lambda that returns the [SpanProcessor] when invoked inside the SDK's
     *        export-config DSL.
     */
    public fun addSpanProcessor(factory: TraceExportConfigDsl.() -> SpanProcessor) {
        customSpanProcessorFactories.add(factory)
    }

    /**
     * Adds resource attributes reported alongside every exported span.
     *
     * @param attributes key/value pairs; supported value types are `String`, `Long`, `Double`, `Boolean`.
     */
    public fun addResourceAttributes(attributes: Map<String, Any>) {
        customResourceAttributes.putAll(attributes)
    }

    /**
     * Toggles verbose telemetry capture. See [isVerbose].
     *
     * @param verbose When `true`, sensitive payloads are emitted unmasked.
     */
    public fun setVerbose(verbose: Boolean) {
        _verbose = verbose
    }

    /**
     * Injects a pre-built Kotlin OpenTelemetry SDK to use for tracing.
     *
     * When set, the feature uses [openTelemetry] as-is and skips internal SDK construction -
     * meaning [addSpanProcessor], [addResourceAttributes], [setServiceInfo], and the
     * `addLangfuseExporter` / `addWeaveExporter` / `addDatadogExporter` integration helpers all
     * become inert. Use this escape hatch when you need full control over the SDK: custom
     * samplers, custom span processors, custom span limits, resource detectors, etc.
     *
     * Build the SDK with `io.opentelemetry.kotlin.createOpenTelemetry { … }` and hand it off:
     *
     * ```kotlin
     * setSdk(
     *     createOpenTelemetry {
     *         tracerProvider {
     *             resource(mapOf("service.name" to "my-agent"))
     *             sampler { parentBased(root = alwaysOn()) }
     *             export { batchSpanProcessor(myExporter) }
     *         }
     *     }
     * )
     * ```
     *
     * The instrumentation scope name/version reported by [tracer] still come from
     * [setServiceInfo] (or the defaults). If you need them inside the SDK's resource attributes,
     * set them in the `resource(...)` block of your `createOpenTelemetry { }` call.
     *
     * @param openTelemetry Pre-built SDK instance to use.
     */
    public fun setSdk(openTelemetry: OpenTelemetry) {
        _sdk = openTelemetry
    }

    /**
     * Toggles whether [OpenTelemetryPlatform.shutdown] runs when the agent closes. See [isShutdownOnAgentClose].
     *
     * @param shutdownOnAgentClose When `true`, the feature shuts down its SDK when the agent closes.
     */
    public fun setShutdownOnAgentClose(shutdownOnAgentClose: Boolean) {
        _shutdownOnAgentClose = shutdownOnAgentClose
    }

    /**
     * Installs a [SpanAdapter] that post-processes every GenAI span. Replaces any previously
     * installed adapter.
     *
     * @param adapter Adapter to install.
     */
    public fun addSpanAdapter(adapter: SpanAdapter) {
        _spanAdapter = adapter
    }

    /**
     * Shuts down the SDK and releases platform-specific resources.
     *
     * Calls `shutdown()` on the [OpenTelemetry] instance (the impl is a [TelemetryCloseable]) -
     * the SDK contract cascades shutdown through the tracer provider to all registered processors,
     * and each processor cascades to its wrapped exporter, releasing any underlying transport
     * resources (e.g., Ktor `HttpClient`).
     *
     * On JVM, [OpenTelemetryPlatform.shutdown] additionally closes the metrics `SdkMeterProvider`.
     */
    internal suspend fun closeSdks() {
        // Call platform-specific shutdown first to make sure all specific resources are released.
        platform.shutdown()

        // Cascade shutdown through the SDK to every registered processor and wrapped exporter.
        val telemetryCloseable = _sdk as? TelemetryCloseable
        if (telemetryCloseable == null) {
            logger.warn { "OpenTelemetry SDK instance is not a TelemetryCloseable. Skip shutdown." }
            return
        }

        logger.debug { "Cascade shutdown for the OpenTelemetry SDK." }
        runCatching {
            telemetryCloseable.shutdown()
        }.onFailure { t ->
            logger.warn(t) { "Failed to shutdown OpenTelemetry SDK." }
        }
    }

    //region Private Methods

    private fun initializeOpenTelemetry(): OpenTelemetry {
        val resourceMap = buildResourceMap()
        val preConfiguredFactories = customSpanProcessorFactories.toList()

        val sdk = createOpenTelemetry {
            tracerProvider {
                resource(resourceMap)
                export {
                    val processors = if (preConfiguredFactories.isEmpty()) {
                        logger.debug { "No custom span processors configured. Using simple stdout processor by default." }
                        listOf(simpleSpanProcessor(stdoutSpanExporter()))
                    } else {
                        preConfiguredFactories.map { factory ->
                            factory().also { processor ->
                                logger.debug { "Adding span processor: ${processor::class.simpleName}" }
                            }
                        }
                    }

                    processors.singleOrNull()
                        ?: compositeSpanProcessor(*processors.toTypedArray())
                }
            }
        }

        return sdk
    }

    internal fun buildResourceMap(): Map<String, Any> = buildMap {
        put("service.name", _serviceName)
        put("service.version", _serviceVersion)
        put("service.instance.id", _instanceId)
        _serviceNamespace?.let { put("service.namespace", it) }

        PlatformInfo.osName?.let { osName -> put("os.type", osName) }
        PlatformInfo.osVersion?.let { osVersion -> put("os.version", osVersion) }
        PlatformInfo.osArch?.let { osArch -> put("os.arch", osArch) }

        putAll(customResourceAttributes)
    }

    //endregion Private Methods
}
