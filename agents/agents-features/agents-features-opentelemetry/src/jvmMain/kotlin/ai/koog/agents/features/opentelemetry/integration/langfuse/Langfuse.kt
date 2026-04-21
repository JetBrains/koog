package ai.koog.agents.features.opentelemetry.integration.langfuse

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
    timeout: Duration? = null,
    traceAttributes: List<CustomAttribute>? = null
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

    val timeout = timeout ?: 10.seconds
    val traceAttributes = traceAttributes ?: emptyList()

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

/**
 * Java-compatible overload of [addLangfuseExporter] that accepts [java.time.Duration] for the timeout parameter.
 *
 * @param langfuseUrl the base URL of the Langfuse instance.
 *        If not set, is retrieved from `LANGFUSE_HOST` environment variable.
 *        Defaults to [https://cloud.langfuse.com](https://cloud.langfuse.com).
 * @param langfusePublicKey if not set, is retrieved from `LANGFUSE_PUBLIC_KEY` environment variable.
 * @param langfuseSecretKey if not set, is retrieved from `LANGFUSE_SECRET_KEY` environment variable.
 * @param timeout OpenTelemetry SpanExporter timeout as [java.time.Duration].
 * @param traceAttributes list of trace-level Langfuse attributes.
 */
public fun OpenTelemetryConfig.addLangfuseExporter(
    langfuseUrl: String?,
    langfusePublicKey: String?,
    langfuseSecretKey: String?,
    timeout: Duration?,
    traceAttributes: List<CustomAttribute>?
) {
    addLangfuseExporterImpl(
        langfuseUrl = langfuseUrl,
        langfusePublicKey = langfusePublicKey,
        langfuseSecretKey = langfuseSecretKey,
        timeout = timeout,
        traceAttributes = traceAttributes
    )
}

private val logger = KotlinLogging.logger { }
