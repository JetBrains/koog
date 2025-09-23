package ai.koog.agents.features.opentelemetry.integration.langfuse

import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetryConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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
public fun OpenTelemetryConfig.addLangfuseExporter(
    langfuseUrl: String? = null,
    langfusePublicKey: String? = null,
    langfuseSecretKey: String? = null,
    timeout: Duration = 10.seconds,
    traceAttributes: List<CustomAttribute> = emptyList()
) {
    val url = langfuseUrl ?: System.getenv()["LANGFUSE_HOST"] ?: "https://cloud.langfuse.com"

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
