package ai.koog.agents.features.opentelemetry.integration.weave

import ai.koog.agents.features.opentelemetry.feature.OpenTelemetryConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal fun OpenTelemetryConfig.addWeaveExporterImpl(
    weaveOtelBaseUrl: String? = null,
    weaveEntity: String? = null,
    weaveProjectName: String? = null,
    weaveApiKey: String? = null,
    timeout: Duration? = null,
) {
    val url = weaveOtelBaseUrl ?: System.getenv()["WEAVE_URL"] ?: "https://trace.wandb.ai"

    logger.debug { "Configured endpoint for Weave telemetry: $url" }

    val entity = requireNotNull(weaveEntity ?: System.getenv()["WEAVE_ENTITY"]) { "WEAVE_ENTITY is not set" }
    val projectName = weaveProjectName ?: System.getenv()["WEAVE_PROJECT_NAME"] ?: "koog-tracing"
    val apiKey = requireNotNull(weaveApiKey ?: System.getenv()["WEAVE_API_KEY"]) { "WEAVE_API_KEY is not set" }
    val timeout = timeout ?: 10.seconds

    val auth = Base64.getEncoder().encodeToString("api:$apiKey".toByteArray(Charsets.UTF_8))

    addSpanExporter(
        OtlpHttpSpanExporter.builder()
            .setTimeout(timeout.inWholeSeconds, TimeUnit.SECONDS)
            .setEndpoint("$url/otel/v1/traces")
            .addHeader("project_id", "$entity/$projectName")
            .addHeader("Authorization", "Basic $auth")
            .build()
    )

    addSpanAdapter(WeaveSpanAdapter(this))
}

/**
 * Configures and adds a Weave Exporter to the OpenTelemetry configuration.
 *
 * @param weaveOtelBaseUrl Optional base URL for the Weave OpenTelemetry endpoint.
 * @param weaveEntity Optional identifier for the Weave entity.
 * @param weaveProjectName Optional name of the Weave project to associate telemetry data with.
 * @param weaveApiKey Optional API key for authenticating with the Weave service.
 * @param timeout Optional timeout duration for interactions with the Weave endpoint.
 */
public fun OpenTelemetryConfig.addWeaveExporter(
    weaveOtelBaseUrl: String? = null,
    weaveEntity: String? = null,
    weaveProjectName: String? = null,
    weaveApiKey: String? = null,
    timeout: Duration? = null
) {
    addWeaveExporterImpl(
        weaveOtelBaseUrl,
        weaveEntity,
        weaveProjectName,
        weaveApiKey,
        timeout
    )
}

private val logger = KotlinLogging.logger { }
