package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.features.opentelemetry.integration.SpanAdapter
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.init.TraceExportConfigDsl
import io.opentelemetry.kotlin.tracing.Tracer
import io.opentelemetry.kotlin.tracing.export.SpanExporter
import io.opentelemetry.kotlin.tracing.export.SpanProcessor
import io.opentelemetry.kotlin.tracing.export.batchSpanProcessor
import io.opentelemetry.kotlin.tracing.export.compositeSpanProcessor

/**
 * API for [OpenTelemetryConfig].
 */
public interface OpenTelemetryConfigAPI {

    /**
     * Indicates whether verbose telemetry data is enabled.
     *
     * When `true`, more detailed span attributes and events are recorded (e.g., full message
     * contents instead of placeholders). Useful for debugging; may increase volume significantly.
     */
    public val isVerbose: Boolean

    /**
     * The Kotlin Multiplatform OpenTelemetry SDK [Tracer] used by the feature.
     */
    public val tracer: Tracer

    /**
     * The service name reported in resource attributes.
     */
    public val serviceName: String

    /**
     * The service version reported in resource attributes.
     */
    public val serviceVersion: String

    /**
     * The service namespace reported in resource attributes, or `null` if not set.
     */
    public val serviceNamespace: String?

    /**
     * Indicates whether OpenTelemetry resources should be shut down when the agent closes.
     *
     * Defaults to `false`. When `true`, the feature's agent-close handler calls the internal
     * SDK-shutdown routine, which shuts down every registered span processor (which in turn shuts
     * down its wrapped exporter) and on JVM also closes the metrics `SdkMeterProvider`. On non-JVM
     * targets the metrics path is a no-op; only span processors are shut down.
     *
     * Note: if multiple agents share the same [OpenTelemetryConfig], enabling this causes shutdown
     * to happen when the first agent closes, which may affect the remaining agents.
     */
    public val isShutdownOnAgentClose: Boolean

    /**
     * Sets the service information reported in resource attributes.
     *
     * @param serviceName Value for the `service.name` resource attribute.
     * @param serviceVersion Value for the `service.version` resource attribute.
     * @param serviceNamespace Value for the `service.namespace` resource attribute, or `null` to omit it.
     */
    public fun setServiceInfo(serviceName: String, serviceVersion: String, serviceNamespace: String? = null)

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
    public fun addSpanExporter(exporter: SpanExporter)

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
    public fun addSpanProcessor(factory: TraceExportConfigDsl.() -> SpanProcessor)

    /**
     * Adds resource attributes reported alongside every exported span.
     *
     * @param attributes key/value pairs; supported value types are `String`, `Long`, `Double`, `Boolean`.
     */
    public fun addResourceAttributes(attributes: Map<String, Any>)

    /**
     * Toggles verbose telemetry capture. See [isVerbose].
     *
     * @param verbose When `true`, sensitive payloads are emitted unmasked.
     */
    public fun setVerbose(verbose: Boolean)

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
    public fun setSdk(openTelemetry: OpenTelemetry)

    /**
     * Toggles whether [OpenTelemetryPlatform.shutdown] runs when the agent closes. See [isShutdownOnAgentClose].
     *
     * @param shutdownOnAgentClose When `true`, the feature shuts down its SDK when the agent closes.
     */
    public fun setShutdownOnAgentClose(shutdownOnAgentClose: Boolean)

    /**
     * Installs a [SpanAdapter] that post-processes every GenAI span. Replaces any previously
     * installed adapter.
     *
     * @param adapter Adapter to install.
     */
    public fun addSpanAdapter(adapter: SpanAdapter)
}
