package ai.koog.agents.features.opentelemetry.integration.langfuse

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetryConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


internal fun OpenTelemetryConfig.addLangfuseExporterImpl(
    langfuseUrl: String? = null,
    langfusePublicKey: String? = null,
    langfuseSecretKey: String? = null,
    timeout: Duration = 10.seconds,
    traceAttributes: List<CustomAttribute> = emptyList()
) {
    val url = langfuseUrl
        ?: System.getenv()["LANGFUSE_HOST"]
        ?: System.getenv()["LANGFUSE_BASE_URL"]
        ?: "https://cloud.langfuse.com"

    logger.debug { "Configured endpoint for Langfuse telemetry: $url" }

    val publicKey =
        requireNotNull(langfusePublicKey ?: System.getenv()["LANGFUSE_PUBLIC_KEY"]) { "LANGFUSE_PUBLIC_KEY is not set" }
    val secretKey =
        requireNotNull(langfuseSecretKey ?: System.getenv()["LANGFUSE_SECRET_KEY"]) { "LANGFUSE_SECRET_KEY is not set" }

    val credentials = "$publicKey:$secretKey"
    val auth = Base64.getEncoder().encodeToString(credentials.toByteArray(Charsets.UTF_8))

    addSpanExporter(
        OtlpHttpSpanExporter.builder()
            .setTimeout(timeout.inWholeSeconds, TimeUnit.SECONDS)
            .setEndpoint("$url/api/public/otel/v1/traces")
            .addHeader("Authorization", "Basic $auth")
            .build()
    )

    addSpanAdapter(LangfuseSpanAdapter(traceAttributes, this))
}

private val logger = KotlinLogging.logger { }
