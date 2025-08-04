package ai.koog.agents.features.opentelemetry.integrations

import ai.koog.agents.features.opentelemetry.feature.OpenTelemetryConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Configure an OpenTelemetry span exporter that sends data to [W&B Weave](https://wandb.ai/site/weave/).
 *
 * @param weaveOtelBaseUrl the URL of the Weave OpenTelemetry endpoint. If not set is retrieved from `WEAVE_URL` environment variable. Defaults to [https://trace.wandb.ai](https://trace.wandb.ai).
 * @param weaveEntity can be found by visiting your W&B dashboard at [https://wandb.ai/home](https://wandb.ai/home) and checking the *Teams* field in the left sidebar.
 * If not set is retrieved from `WEAVE_ENTITY` environment variable.
 * @param weaveProjectName name of your Weave project. If not set is retrieved from `WEAVE_PROJECT_NAME` environment variable.
 * @param weaveApiKey can be created on the [https://wandb.ai/authorize](https://wandb.ai/authorize) page. If not set is retrieved from `WEAVE_API_KEY` environment variable.
 * @param timeout OpenTelemetry SpanExporter timeout. See [io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporterBuilder.setTimeout].
 *
 * @see <a href="https://weave-docs.wandb.ai/guides/tracking/otel/">Weave OpenTelemetry Docs</a>
 */
public fun OpenTelemetryConfig.addWeaveExporter(
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
}

private val logger = KotlinLogging.logger {}
