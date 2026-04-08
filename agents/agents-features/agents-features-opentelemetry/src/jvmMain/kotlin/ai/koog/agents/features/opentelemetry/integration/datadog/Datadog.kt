package ai.koog.agents.features.opentelemetry.integration.datadog

import ai.koog.agents.features.opentelemetry.feature.OpenTelemetryConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.sdk.trace.export.SpanExporter
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Builds a Datadog-ready [SpanExporter] that sends OTLP traces directly to Datadog's intake endpoint.
 *
 * Returns the exporter without registering it, so callers can wrap it with
 * additional decorators before calling [OpenTelemetryConfig.addSpanExporter].
 *
 * Requires a `DD_API_KEY` environment variable (or explicit [datadogApiKey] parameter).
 * The `dd-otlp-source: llmobs` header routes spans to the LLM Observability product.
 *
 * @param datadogApiKey Datadog API key. Falls back to `DD_API_KEY` environment variable.
 * @param datadogSite Datadog site (e.g. `datadoghq.com`, `datadoghq.eu`).
 *        Falls back to `DD_SITE` environment variable, defaults to `datadoghq.com`.
 * @param timeout OpenTelemetry SpanExporter timeout.
 * @return a [SpanExporter] configured for Datadog direct OTLP intake.
 *
 * @see <a href="https://docs.datadoghq.com/opentelemetry/guide/otlp_api/">Datadog OTLP API Intake</a>
 */
public fun buildDatadogExporter(
    datadogApiKey: String? = null,
    datadogSite: String? = null,
    timeout: Duration = 10.seconds,
): SpanExporter {
    val apiKey = datadogApiKey
        ?: System.getenv("DD_API_KEY")
        ?: error("DD_API_KEY environment variable is required for Datadog direct intake")

    val site = datadogSite ?: System.getenv("DD_SITE") ?: "datadoghq.com"
    val endpoint = "https://otlp.$site/v1/traces"

    logger.debug { "Configuring Datadog direct intake exporter: endpoint=$endpoint" }

    return OtlpHttpSpanExporter.builder()
        .setTimeout(timeout.inWholeSeconds, TimeUnit.SECONDS)
        .setEndpoint(endpoint)
        .addHeader("dd-api-key", apiKey)
        .addHeader("dd-otlp-source", "llmobs")
        .build()
}

internal fun OpenTelemetryConfig.addDatadogExporterImpl(
    datadogApiKey: String? = null,
    datadogSite: String? = null,
    timeout: Duration? = null,
    traceAttributes: Map<String, String>? = null,
) {
    addSpanExporter(buildDatadogExporter(datadogApiKey, datadogSite, timeout ?: 10.seconds))

    if (traceAttributes != null && traceAttributes.isNotEmpty()) {
        addResourceAttributes(
            traceAttributes.map { (key, value) ->
                AttributeKey.stringKey(key) to value
            }.toMap()
        )
    }
}

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
public fun OpenTelemetryConfig.addDatadogExporter(
    datadogApiKey: String? = null,
    datadogSite: String? = null,
    timeout: Duration? = null,
    traceAttributes: Map<String, String>? = null,
) {
    addDatadogExporterImpl(datadogApiKey, datadogSite, timeout, traceAttributes)
}

private val logger = KotlinLogging.logger { }
