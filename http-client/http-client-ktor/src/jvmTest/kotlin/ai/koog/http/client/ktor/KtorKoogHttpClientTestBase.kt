package ai.koog.http.client.ktor

import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.post
import ai.koog.http.client.test.BaseKoogHttpClientTest
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
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull

abstract class KtorKoogHttpClientTestBase : BaseKoogHttpClientTest() {

    @Test
    override fun `test return success string response on get`() =
        super.`test return success string response on get`()

    @Test
    override fun `test return success string response on post`() =
        super.`test return success string response on post`()

    @Test
    override fun `test post request headers override inferred string content type`() =
        super.`test post request headers override inferred string content type`()

    @Test
    override fun `test post JSON request and get JSON response`() =
        super.`test post JSON request and get JSON response`()

    @Test
    override fun `test handle on non-success status`() =
        super.`test handle on non-success status`()

    @Test
    override fun `test get SSE flow and collect events`() =
        super.`test get SSE flow and collect events`()

    @Test
    override fun `test filter SSE events`() =
        super.`test filter SSE events`()

    @Test
    override fun `test return success string response on get with parameters`() {
        super.`test return success string response on get with parameters`()
    }

    @Test
    override fun `test return success string response on post with parameters`() {
        super.`test return success string response on post with parameters`()
    }

    @Test
    override fun `test lines emits non-blank lines`() =
        super.`test lines emits non-blank lines`()

    @Test
    override fun `test lines request headers override inferred string content type`() =
        super.`test lines request headers override inferred string content type`()

    @Test
    override fun `test lines skips blank lines`() =
        super.`test lines skips blank lines`()

    @Test
    override fun `test lines emits nothing for empty body`() =
        super.`test lines emits nothing for empty body`()

    @Test
    override fun `test lines surfaces non-2xx as KoogHttpClientException`() =
        super.`test lines surfaces non-2xx as KoogHttpClientException`()

    @Test
    override fun `test lines propagates cancellation`() =
        super.`test lines propagates cancellation`()

    abstract fun ktorClient(
        baseClient: HttpClient,
        baseUrl: String = "",
        json: Json = Json,
        headers: Map<String, String> = emptyMap(),
        queryParameters: Map<String, String> = emptyMap(),
        withSse: Boolean = true,
    ): KoogHttpClient

    @Test
    fun `factory overload should configure base url headers query parameters and json serialization`() = runTest {
        var capturedRequest: HttpRequestData? = null
        var capturedBody: String? = null
        val engine = MockEngine { request ->
            capturedRequest = request
            capturedBody = (request.body as TextContent).text

            respond(
                content = """{"response":"ok"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = ktorClient(
            baseClient = HttpClient(engine),
            baseUrl = "https://example.test/api",
            json = Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                explicitNulls = false
            },
            headers = mapOf("Authorization" to "Bearer token"),
            queryParameters = mapOf("tenant" to "acme")
        )

        val response = client.post<TestRequest, TestResponse>(
            path = "v1/messages",
            request = TestRequest("hello")
        )

        assertEquals("ok", response.response)
        val request = requireNotNull(capturedRequest)
        assertEquals("https://example.test/api/v1/messages?tenant=acme", request.url.toString())
        assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
        assertEquals("""{"request":"hello"}""", capturedBody)

        val ktorClient = (client as KtorKoogHttpClient).ktorClient
        assertNotNull(ktorClient.plugin(HttpTimeout))
        assertNotNull(ktorClient.plugin(SSE))
        client.close()
    }

    @Test
    fun `factory overload should not install sse plugin when disabled`() {
        val client = ktorClient(
            baseClient = HttpClient(MockEngine { error("No HTTP call expected") }),
            baseUrl = "https://example.test/api",
            json = Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                explicitNulls = false
            },
            withSse = false
        )

        val ktorClient = (client as KtorKoogHttpClient).ktorClient
        val exception = assertFails { ktorClient.plugin(SSE) }

        assertTrue(exception.message?.contains("SSE") == true || exception::class.simpleName?.contains("Plugin") == true)
        assertFalse(runCatching { ktorClient.plugin(SSE) }.isSuccess)
        client.close()
    }
}
