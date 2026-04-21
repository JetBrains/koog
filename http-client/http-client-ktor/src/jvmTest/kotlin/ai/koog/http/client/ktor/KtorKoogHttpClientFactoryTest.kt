package ai.koog.http.client.ktor

import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.post
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.plugin
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KtorKoogHttpClientFactoryTest {
    @Serializable
    private data class TestRequest(val message: String)

    @Serializable
    private data class TestResponse(val status: String)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Test
    fun `factory overload should configure base url headers query parameters and json serialization`() = runTest {
        var capturedRequest: HttpRequestData? = null
        var capturedBody: String? = null
        val engine = MockEngine { request ->
            capturedRequest = request
            capturedBody = (request.body as TextContent).text

            respond(
                content = """{"status":"ok"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = KoogHttpClient.fromKtorClient(
            clientName = "FactoryTestClient",
            logger = KotlinLogging.logger("FactoryTestLogger"),
            baseClient = HttpClient(engine),
            baseUrl = "https://example.test/api",
            requestTimeoutMillis = 1_000,
            connectTimeoutMillis = 2_000,
            socketTimeoutMillis = 3_000,
            json = json,
            headers = mapOf("Authorization" to "Bearer token"),
            queryParameters = mapOf("tenant" to "acme")
        )

        val response = client.post<TestRequest, TestResponse>(
            path = "v1/messages",
            request = TestRequest("hello")
        )

        assertEquals("ok", response.status)
        val request = requireNotNull(capturedRequest)
        assertEquals("https://example.test/api/v1/messages?tenant=acme", request.url.toString())
        assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
        assertEquals("""{"message":"hello"}""", capturedBody)

        val ktorClient = (client as KtorKoogHttpClient).ktorClient
        assertNotNull(ktorClient.plugin(HttpTimeout))
        assertNotNull(ktorClient.plugin(SSE))
        client.close()
    }

    @Test
    fun `factory overload should not install sse plugin when disabled`() {
        val client = KoogHttpClient.fromKtorClient(
            clientName = "NoSseClient",
            logger = KotlinLogging.logger("NoSseLogger"),
            baseClient = HttpClient(MockEngine { error("No HTTP call expected") }),
            baseUrl = "https://example.test/api",
            requestTimeoutMillis = 1_000,
            connectTimeoutMillis = 2_000,
            socketTimeoutMillis = 3_000,
            json = json,
            withSse = false
        )

        val ktorClient = (client as KtorKoogHttpClient).ktorClient
        val exception = assertFails { ktorClient.plugin(SSE) }

        assertTrue(exception.message?.contains("SSE") == true || exception::class.simpleName?.contains("Plugin") == true)
        assertFalse(runCatching { ktorClient.plugin(SSE) }.isSuccess)
        client.close()
    }
}
