package ai.koog.agents.features.opentelemetry.integration.langfuse

import ai.koog.agents.core.system.getEnvironmentVariableOrNull
import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetryConfig
import ai.koog.agents.features.opentelemetry.integration.otlp.OtlpJsonSpanExporter
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.jvm.JvmOverloads
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger { }

private const val DEFAULT_LANGFUSE_URL = "https://cloud.langfuse.com"

private val defaultRequestTimeout = 10.seconds

/**
 * Installs the Langfuse [SpanAdapter][ai.koog.agents.features.opentelemetry.integration.SpanAdapter]
 * without registering the Langfuse cloud exporter. Spans are reshaped to the Langfuse-recommended
 * attribute layout but are emitted only through whatever exporters the caller registers separately.
 *
 * Useful for tests that want to assert on Langfuse-shaped spans without performing a real HTTP
 * round-trip and for integrators who want the Langfuse transformations applied to spans they ship
 * to a different backend.
 *
 * @param traceAttributes list of trace-level Langfuse attributes
 *        (see https://langfuse.com/integrations/native/opentelemetry#propagating-attributes).
 */
@JvmOverloads
public fun OpenTelemetryConfig.addLangfuseSpanAdapter(
    traceAttributes: List<CustomAttribute>? = null,
) {
    addSpanAdapter(LangfuseSpanAdapter(traceAttributes ?: emptyList(), this))
}

/**
 * Configures a span exporter that sends data to [Langfuse](https://langfuse.com/) and installs the
 * Langfuse [SpanAdapter][ai.koog.agents.features.opentelemetry.integration.SpanAdapter] that
 * reshapes spans to the Langfuse-recommended attribute layout.
 *
 * Uses the Kotlin Multiplatform OTLP/JSON exporter - works on every target supported by the
 * OpenTelemetry feature. Authenticates via HTTP Basic with `LANGFUSE_PUBLIC_KEY` and
 * `LANGFUSE_SECRET_KEY` (passed in or read from environment variables).
 *
 * Registered via [addSpanExporter][OpenTelemetryConfig.addSpanExporter], which wraps the exporter
 * in a batch span processor - the cloud HTTP round-trip happens on a worker thread instead of
 * blocking the agent on each span end.
 *
 * For test or non-cloud setups that need only the span transformations, see [addLangfuseSpanAdapter].
 *
 * @param langfuseUrl base Langfuse URL;
 * @param langfusePublicKey Langfuse public key. Defaults to `LANGFUSE_PUBLIC_KEY` env var;
 * @param langfuseSecretKey Langfuse secret key. Defaults to `LANGFUSE_SECRET_KEY` env var;
 * @param timeout request timeout (10 seconds by default);
 * @param traceAttributes list of trace-level Langfuse attributes
 *        (see https://langfuse.com/integrations/native/opentelemetry#propagating-attributes).
 */
@OptIn(ExperimentalEncodingApi::class)
@JvmOverloads
public fun OpenTelemetryConfig.addLangfuseExporter(
    langfuseUrl: String? = null,
    langfusePublicKey: String? = null,
    langfuseSecretKey: String? = null,
    timeout: Duration? = null,
    traceAttributes: List<CustomAttribute>? = null,
) {
    val url = langfuseUrl
        ?: getEnvironmentVariableOrNull("LANGFUSE_HOST")
        ?: getEnvironmentVariableOrNull("LANGFUSE_BASE_URL")
        ?: DEFAULT_LANGFUSE_URL

    logger.debug { "Configured endpoint for Langfuse telemetry: $url" }

    val publicKey = requireNotNull(
        langfusePublicKey ?: getEnvironmentVariableOrNull("LANGFUSE_PUBLIC_KEY"),
    ) { "LANGFUSE_PUBLIC_KEY is not set" }

    val secretKey = requireNotNull(
        langfuseSecretKey ?: getEnvironmentVariableOrNull("LANGFUSE_SECRET_KEY"),
    ) { "LANGFUSE_SECRET_KEY is not set" }

    val auth = Base64.encode("$publicKey:$secretKey".encodeToByteArray())

    addSpanExporter(
        OtlpJsonSpanExporter(
            endpoint = "$url/api/public/otel/v1/traces",
            headers = mapOf("Authorization" to "Basic $auth"),
            timeout = timeout ?: defaultRequestTimeout,
        )
    )

    addLangfuseSpanAdapter(traceAttributes)
}
