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
    timeout: Duration = 10.seconds,
) {
    val url = weaveOtelBaseUrl ?: System.getenv()["WEAVE_URL"] ?: "https://trace.wandb.ai"

    logger.debug { "Configured endpoint for Weave telemetry: $url" }

    val entity = requireNotNull(weaveEntity ?: System.getenv()["WEAVE_ENTITY"]) { "WEAVE_ENTITY is not set" }
    val projectName = weaveProjectName ?: System.getenv()["WEAVE_PROJECT_NAME"] ?: "koog-tracing"
    val apiKey = requireNotNull(weaveApiKey ?: System.getenv()["WEAVE_API_KEY"]) { "WEAVE_API_KEY is not set" }

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

private val logger = KotlinLogging.logger { }
