package ai.koog.agents.features.opentelemetry.integration.datadog

import ai.koog.agents.core.system.getEnvironmentVariableOrNull
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetryConfig
import ai.koog.agents.features.opentelemetry.integration.otlp.OtlpJsonSpanExporter
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.jvm.JvmOverloads
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger { }

private const val DEFAULT_DATADOG_URL = "datadoghq.com"

private val defaultRequestTimeout = 10.seconds

/**
 * Configure an OpenTelemetry span exporter that sends data to [Datadog](https://www.datadoghq.com/)
 * via direct OTLP intake.
 *
 * Registered via [addSpanExporter][OpenTelemetryConfig.addSpanExporter], which wraps the exporter
 * in a batch span processor - the cloud HTTP round-trip happens on a worker thread instead of
 * blocking the agent on each span end.
 *
 * @param datadogApiKey Datadog API key. If not set, is retrieved from `DD_API_KEY` environment variable;
 * @param url Datadog site. If not set, is retrieved from `DD_SITE` environment variable. Defaults to `datadoghq.com`;
 * @param timeout request timeout (10 seconds by default);
 * @param resourceAttributes resource-level attributes to add to all exported spans.
 *
 * @see <a href="https://docs.datadoghq.com/opentelemetry/guide/otlp_api/">Datadog OTLP API Intake</a>
 * @see <a href="https://docs.datadoghq.com/llm_observability/">Datadog LLM Observability</a>
 */
@JvmOverloads
public fun OpenTelemetryConfig.addDatadogExporter(
    datadogApiKey: String? = null,
    url: String? = null,
    timeout: Duration? = null,
    resourceAttributes: Map<String, String>? = null,
) {
    val apiKey = datadogApiKey
        ?: getEnvironmentVariableOrNull("DD_API_KEY")
        ?: error("Datadog API key is missing. Pass it explicitly or set the DD_API_KEY environment variable.")

    val site = url ?: getEnvironmentVariableOrNull("DD_SITE") ?: DEFAULT_DATADOG_URL
    val endpoint = "https://otlp.$site/v1/traces"

    logger.debug { "Configuring Datadog direct intake exporter: endpoint=$endpoint" }

    addSpanExporter(
        OtlpJsonSpanExporter(
            endpoint = endpoint,
            headers = mapOf(
                "dd-api-key" to apiKey,
                "dd-otlp-source" to "llmobs",
            ),
            timeout = timeout ?: defaultRequestTimeout,
        )
    )

    if (!resourceAttributes.isNullOrEmpty()) {
        addResourceAttributes(resourceAttributes.toMap())
    }
}
