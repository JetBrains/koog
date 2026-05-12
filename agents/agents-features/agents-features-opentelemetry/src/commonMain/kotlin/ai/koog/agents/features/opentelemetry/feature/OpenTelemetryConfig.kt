package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.features.opentelemetry.integration.SpanAdapter
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.createOpenTelemetry
import io.opentelemetry.kotlin.factory.ContextFactory
import io.opentelemetry.kotlin.init.TraceExportConfigDsl
import io.opentelemetry.kotlin.tracing.Tracer
import io.opentelemetry.kotlin.tracing.export.SpanExporter
import io.opentelemetry.kotlin.tracing.export.SpanProcessor

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
 * [createOpenTelemetry] and inject it via [setSdk] - in that case the
 * other configuration setters become inert.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public expect class OpenTelemetryConfig internal constructor(
    delegate: OpenTelemetryConfigImpl,
) : FeatureConfig, OpenTelemetryConfigAPI {

    /**
     * Constructs a new [OpenTelemetryConfig] with default service information.
     */
    public constructor()

    internal val delegate: OpenTelemetryConfigImpl

    /**
     * Holds JVM-only state threaded through by extension functions in the jvmMain.
     */
    internal val platform: OpenTelemetryPlatform

    /**
     * Kotlin SDK context factory for parent-child span wiring.
     */
    internal val contextFactory: ContextFactory

    /**
     * The span adapter used to post-process every GenAI span before it is finalized.
     */
    internal val spanAdapter: SpanAdapter?

    /**
     * Instrumentation scope name reported to the tracer; frozen once the SDK is built.
     */
    internal val instrumentationScopeName: String

    /**
     * Builds the resource attributes map (service info, OS info, custom attributes).
     */
    internal fun buildResourceMap(): Map<String, Any>

    /**
     * Shuts down the SDK and releases platform-specific resources.
     *
     * Calls `shutdown()` on the [OpenTelemetry] instance - the SDK contract cascades shutdown
     * through the tracer provider to all registered processors, and each processor cascades to
     * its wrapped exporter, releasing any underlying transport resources (e.g., Ktor `HttpClient`).
     *
     * On JVM, [OpenTelemetryPlatform.shutdown] additionally closes the metrics `SdkMeterProvider`.
     */
    internal suspend fun closeSdks()

    override val isVerbose: Boolean
    override val tracer: Tracer
    override val serviceName: String
    override val serviceVersion: String
    override val serviceNamespace: String?
    override val isShutdownOnAgentClose: Boolean
    override fun setServiceInfo(
        serviceName: String,
        serviceVersion: String,
        serviceNamespace: String?
    )

    override fun addSpanExporter(exporter: SpanExporter)
    override fun addSpanProcessor(factory: TraceExportConfigDsl.() -> SpanProcessor)
    override fun addResourceAttributes(attributes: Map<String, Any>)
    override fun setVerbose(verbose: Boolean)
    override fun setSdk(openTelemetry: OpenTelemetry)
    override fun setShutdownOnAgentClose(shutdownOnAgentClose: Boolean)
    override fun addSpanAdapter(adapter: SpanAdapter)
}
