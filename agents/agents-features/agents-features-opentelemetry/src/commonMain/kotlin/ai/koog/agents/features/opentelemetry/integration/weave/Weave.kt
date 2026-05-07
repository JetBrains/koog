package ai.koog.agents.features.opentelemetry.integration.weave

import ai.koog.agents.core.system.getEnvironmentVariableOrNull
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetryConfig
import ai.koog.agents.features.opentelemetry.integration.otlp.OtlpJsonSpanExporter
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.jvm.JvmOverloads
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger { }

private const val DEFAULT_WEAVE_URL = "https://trace.wandb.ai"

private val defaultRequestTimeout = 10.seconds

/**
 * Installs the W&B Weave [SpanAdapter][ai.koog.agents.features.opentelemetry.integration.SpanAdapter]
 * without registering the Weave cloud exporter. Spans are reshaped to the Weave-recommended
 * attribute layout but are emitted only through whatever exporters the caller registers separately.
 *
 * Useful for tests that want to assert on Weave-shaped spans without performing a real HTTP
 * round-trip and for integrators who want the Weave transformations applied to spans they ship to
 * a different backend.
 */
public fun OpenTelemetryConfig.addWeaveSpanAdapter() {
    addSpanAdapter(WeaveSpanAdapter(this))
}

/**
 * Configures a span exporter that sends data to [W&B Weave](https://wandb.ai/site/weave/) and
 * installs the Weave [SpanAdapter][ai.koog.agents.features.opentelemetry.integration.SpanAdapter]
 * that reshapes spans to the Weave-recommended attribute layout.
 *
 * Uses the Kotlin Multiplatform OTLP/JSON exporter - works on every target supported by the
 * OpenTelemetry feature. Authenticates via HTTP Basic with the W&B API key.
 *
 * Registered via [addSpanExporter][OpenTelemetryConfig.addSpanExporter], which wraps the exporter
 * in a batch span processor - the cloud HTTP round-trip happens on a worker thread instead of
 * blocking the agent on each span end.
 *
 * For test or non-cloud setups that need only the span transformations, see [addWeaveSpanAdapter].
 *
 * @param weaveOtelBaseUrl base Weave URL;
 * @param weaveEntity W&B Weave entity (org/team);
 * @param weaveProjectName W&B Weave project name;
 * @param weaveApiKey W&B API key;
 * @param timeout request timeout (10 seconds by default).
 */
@OptIn(ExperimentalEncodingApi::class)
@JvmOverloads
public fun OpenTelemetryConfig.addWeaveExporter(
    weaveOtelBaseUrl: String? = null,
    weaveEntity: String? = null,
    weaveProjectName: String? = null,
    weaveApiKey: String? = null,
    timeout: Duration? = null,
) {
    val url = weaveOtelBaseUrl
        ?: getEnvironmentVariableOrNull("WEAVE_URL")
        ?: DEFAULT_WEAVE_URL

    logger.debug { "Configured endpoint for Weave telemetry: $url" }

    val entity = requireNotNull(
        weaveEntity ?: getEnvironmentVariableOrNull("WEAVE_ENTITY"),
    ) { "WEAVE_ENTITY is not set" }

    val projectName = weaveProjectName
        ?: getEnvironmentVariableOrNull("WEAVE_PROJECT_NAME")
        ?: "koog-tracing"

    val apiKey = requireNotNull(
        weaveApiKey ?: getEnvironmentVariableOrNull("WEAVE_API_KEY"),
    ) { "WEAVE_API_KEY is not set" }

    val auth = Base64.encode("api:$apiKey".encodeToByteArray())

    addSpanExporter(
        OtlpJsonSpanExporter(
            endpoint = "$url/otel/v1/traces",
            headers = mapOf(
                "project_id" to "$entity/$projectName",
                "Authorization" to "Basic $auth",
            ),
            timeout = timeout ?: defaultRequestTimeout,
        )
    )

    addWeaveSpanAdapter()
}
