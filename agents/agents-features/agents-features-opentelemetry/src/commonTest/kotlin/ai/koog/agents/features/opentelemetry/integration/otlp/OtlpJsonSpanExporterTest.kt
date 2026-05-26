package ai.koog.agents.features.opentelemetry.integration.otlp

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.tracing.SpanKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class OtlpJsonSpanExporterTest {

    /**
     * A Ktor [HttpClient] paired with captured request metadata and raw body bytes.
     * The body is read inside the mock handler using the public `OutgoingContent.toByteArray`
     * extension from `io.ktor.client.engine.mock`, avoiding post-hoc internal-type casts.
     */
    private data class CapturingClient(
        val client: HttpClient,
        val capturedRequests: MutableList<HttpRequestData>,
        val capturedBodies: MutableList<ByteArray>,
    )

    private val testJson = Json {
        encodeDefaults = false
        explicitNulls = false
        ignoreUnknownKeys = true
    }

    @Test
    fun testPostsToConfiguredEndpointWithBearerHeader() = runTest {
        val mock = mockClient()
        val exporter = OtlpJsonSpanExporter(
            endpoint = "https://example.test/v1/traces",
            headers = mapOf("Authorization" to "Basic abcdef"),
            baseClient = mock.client,
        )

        val span = FakeSpanData(
            name = "agent.run",
            spanKind = SpanKind.INTERNAL,
            attributes = mapOf("key" to "value"),
        )

        val result = exporter.export(listOf(span))
        assertEquals(OperationResultCode.Success, result)

        val request = mock.capturedRequests.singleOrNull()
        assertNotNull(request)

        assertEquals(HttpMethod.Post, request.method)
        assertEquals("https://example.test/v1/traces", request.url.toString())
        assertEquals("Basic abcdef", request.headers[HttpHeaders.Authorization])
        assertEquals(true, request.headers[HttpHeaders.UserAgent]?.startsWith("koog-otlp-exporter"))
    }

    @Test
    fun testSerializesPayloadAsOtlpExportRequest() = runTest {
        val mock = mockClient()
        val exporter = OtlpJsonSpanExporter(
            endpoint = "https://example.test/v1/traces",
            baseClient = mock.client,
        )

        val span = FakeSpanData(
            name = "agent.run",
            attributes = mapOf("key" to "value"),
        )

        exporter.export(listOf(span))

        val text = mock.capturedBodies.single().decodeToString()
        val parsed = testJson.decodeFromString(OtlpExportRequest.serializer(), text)
        val otlpSpan = parsed.resourceSpans.single().scopeSpans.single().spans.single()

        assertEquals("agent.run", otlpSpan.name)
        assertEquals(span.spanContext.traceId, otlpSpan.traceId)
        assertEquals(span.spanContext.spanId, otlpSpan.spanId)

        // int64 timestamps must be JSON strings.
        assertNotNull(otlpSpan.startTimeUnixNano)

        // Attribute round-trip.
        val actualAttribute = otlpSpan.attributes?.find { it.key == "key" }
        assertNotNull(actualAttribute)
        assertEquals(
            "value",
            actualAttribute.value.stringValue,
        )
    }

    @Test
    fun testReturnsFailureOnNon2xxResponse() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { respondError(HttpStatusCode.InternalServerError) }
            }
            install(ContentNegotiation) { json(testJson) }
        }
        val exporter = OtlpJsonSpanExporter(
            endpoint = "https://example.test/v1/traces",
            baseClient = client,
        )

        val result = exporter.export(listOf(FakeSpanData()))
        assertEquals(OperationResultCode.Failure, result)
    }

    @Test
    fun testReturnsSuccessForEmptyBatch() = runTest {
        val mock = mockClient()
        val exporter = OtlpJsonSpanExporter(
            endpoint = "https://example.test/v1/traces",
            baseClient = mock.client,
        )

        assertEquals(OperationResultCode.Success, exporter.export(emptyList()))

        // No HTTP traffic for an empty batch.
        assertEquals(0, mock.capturedRequests.size)
    }

    @Test
    fun testForceFlushReturnsSuccess() = runTest {
        val mock = mockClient()
        val exporter = OtlpJsonSpanExporter(
            endpoint = "https://example.test/v1/traces",
            baseClient = mock.client,
        )
        assertEquals(OperationResultCode.Success, exporter.forceFlush())
    }

    @Test
    fun testShutdownReturnsSuccess() = runTest {
        val mock = mockClient()
        val exporter = OtlpJsonSpanExporter(
            endpoint = "https://example.test/v1/traces",
            baseClient = mock.client,
        )
        assertEquals(OperationResultCode.Success, exporter.shutdown())
    }

    @OptIn(InternalAPI::class)
    @Test
    fun testShutdownReturnsFailureWhenClientCloseThrows() = runTest {
        val throwingEngineFactory = object : HttpClientEngineFactory<HttpClientEngineConfig> {
            override fun create(block: HttpClientEngineConfig.() -> Unit): HttpClientEngine =
                object : HttpClientEngineBase("throwing-close") {
                    override val config = HttpClientEngineConfig().apply(block)
                    override val dispatcher = Dispatchers.Unconfined
                    override suspend fun execute(data: HttpRequestData): HttpResponseData = error("not used")
                    override fun close(): Unit = throw IllegalStateException("simulated close failure")
                }
        }
        val exporter = OtlpJsonSpanExporter(
            endpoint = "https://example.test/v1/traces",
            baseClient = HttpClient(throwingEngineFactory),
        )
        assertEquals(OperationResultCode.Failure, exporter.shutdown())
    }

    //region Private Methods

    private fun mockClient(): CapturingClient {
        val capturedRequests = mutableListOf<HttpRequestData>()
        val capturedBodies = mutableListOf<ByteArray>()
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    capturedRequests += request
                    capturedBodies += request.body.toByteArray()
                    respond(ByteReadChannel.Empty, HttpStatusCode.OK, headersOf())
                }
            }

            install(ContentNegotiation) { json(testJson) }
        }

        return CapturingClient(client, capturedRequests, capturedBodies)
    }

    //endregion Private Methods
}
