package ai.koog.agents.features.opentelemetry.integration.otlp

import ai.koog.agents.features.opentelemetry.integration.otlp.OtlpSpanDataMapper.toOtlpExportRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.tracing.data.SpanData
import io.opentelemetry.kotlin.tracing.export.SpanExporter
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Kotlin Multiplatform OTLP/JSON `SpanExporter` implementation.
 *
 * Sends spans over HTTP to any OTLP/JSON-compatible receiver (Langfuse, W&B Weave, generic collectors).
 * The wire format follows https://opentelemetry.io/docs/specs/otlp/#json-protobuf-encoding with
 * `Content-Type: application/json` and the JSON-encoded `ExportTraceServiceRequest`.
 *
 * On non-2xx responses or transport errors, returns [OperationResultCode.Failure]; the configured
 * span processor (typically `BatchSpanProcessor`) drops the failed batch.
 *
 * Note: There is no built-in retry / back-off.
 *
 * @param endpoint full OTLP/JSON URL, e.g. `https://cloud.langfuse.com/api/public/otel/v1/traces`.
 * @param headers extra HTTP headers (typically `Authorization`);
 * @param timeout request timeout, by default, set to 10 seconds;
 * @param baseClient optional [HttpClient]. When provided, the exporter uses it as-is and skips
 *        the default `ContentNegotiation` install - the caller must provide a JSON serializer.
 */
public class OtlpJsonSpanExporter(
    private val endpoint: String,
    private val headers: Map<String, String> = emptyMap(),
    private val timeout: Duration? = null,
    private val baseClient: HttpClient? = null,
) : SpanExporter {

    private companion object {
        private val logger = KotlinLogging.logger { }

        private val json: Json = Json {
            encodeDefaults = false
            explicitNulls = false
            ignoreUnknownKeys = true
        }

        /**
         * Default `User-Agent` header value mirroring the Java SDK's: `OTel-OTLP-Exporter-Java/<ver>`.
         */
        const val OTLP_JSON_USER_AGENT: String = "koog-otlp-exporter"

        /**
         * Default request timeout, by default, set to 10 seconds.
         */
        val defaultTimeout: Duration = 10.seconds
    }

    private val client: HttpClient = (baseClient ?: HttpClient()).prepare()

    override suspend fun export(telemetry: List<SpanData>): OperationResultCode {
        if (telemetry.isEmpty()) {
            return OperationResultCode.Success
        }

        val payload = telemetry.toOtlpExportRequest()

        return runCatching {
            val response: HttpResponse = client.post {
                setBody(payload)
            }
            if (response.status.isSuccess()) {
                OperationResultCode.Success
            } else {
                val body = runCatching { response.bodyAsText() }.getOrElse { "<unreadable>" }
                logger.warn {
                    "OTLP/JSON export failed: ${response.status} (${response.status.description}). " +
                        "Response body: $body"
                }
                OperationResultCode.Failure
            }
        }.getOrElse { error ->
            logger.warn(error) { "OTLP/JSON export failed with exception" }
            OperationResultCode.Failure
        }
    }

    override suspend fun forceFlush(): OperationResultCode =
        OperationResultCode.Success

    override suspend fun shutdown(): OperationResultCode {
        runCatching {
            client.close()
        }.onFailure { error ->
            logger.warn(error) { "Failed to close OTLP/JSON HTTP client" }
            return OperationResultCode.Failure
        }

        return OperationResultCode.Success
    }

    //region Private Methods

    private fun HttpClient.prepare(): HttpClient {
        logger.debug { "Configuring OTLP/JSON HTTP client for endpoint: $endpoint" }

        return this.config {
            defaultRequest {
                url(endpoint)
                contentType(ContentType.Application.Json)
                headers {
                    append(HttpHeaders.Accept, ContentType.Application.Json.toString())
                    append(HttpHeaders.UserAgent, OTLP_JSON_USER_AGENT)

                    // Append provided headers
                    this@OtlpJsonSpanExporter.headers.forEach { (name, value) ->
                        append(name, value)
                    }
                }
            }

            install(ContentNegotiation) {
                json(json)
            }

            install(HttpTimeout) {
                val timeout = timeout ?: defaultTimeout

                requestTimeoutMillis = timeout.inWholeMilliseconds
                connectTimeoutMillis = timeout.inWholeMilliseconds
                socketTimeoutMillis = timeout.inWholeMilliseconds
            }
        }
    }

    //endregion Private Methods
}
