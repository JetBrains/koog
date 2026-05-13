package ai.koog.prompt.executor.clients.siliconflow

import ai.koog.prompt.executor.clients.LLMClientException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SiliconFlowLLMClientEmbeddingTest {

    private val apiKey = "test-api-key"

    //language=json
    private val successfulEmbeddingResponse = """
        {
            "data": [{"embedding": [0.1, 0.2, 0.3, 0.4, 0.5], "index": 0}],
            "model": "BAAI/bge-m3",
            "usage": {"prompt_tokens": 5, "total_tokens": 5}
        }
    """.trimIndent()

    //language=json
    private val emptyDataResponse = """
        {
            "data": [],
            "model": "BAAI/bge-m3"
        }
    """.trimIndent()

    //language=json
    private val errorResponse = """
        {
            "data": [],
            "model": "",
            "error": {"message": "Invalid API key", "type": "invalid_request_error", "code": "401"}
        }
    """.trimIndent()

    @Test
    fun testEmbedReturnsEmbeddingVectorOnSuccess() = runTest {
        var capturedUrl = ""
        var capturedMethod: HttpMethod? = null
        var capturedAuth: String? = null

        val engine = MockEngine { req ->
            capturedUrl = req.url.toString()
            capturedMethod = req.method
            capturedAuth = req.headers[HttpHeaders.Authorization]
            respond(
                content = successfulEmbeddingResponse,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val http = HttpClient(engine) {}
        val client = SiliconFlowLLMClient(apiKey = apiKey, baseClient = http)

        val embedding = client.embed(
            text = "Hello, world!",
            model = SiliconFlowModels.Embeddings.BgeM3
        )

        assertTrue(capturedUrl.startsWith("https://api.siliconflow.cn/"))
        assertTrue(capturedUrl.endsWith("v1/embeddings"))
        assertEquals(HttpMethod.Post, capturedMethod)
        assertEquals("Bearer $apiKey", capturedAuth)
        assertEquals(listOf(0.1, 0.2, 0.3, 0.4, 0.5), embedding)
    }

    @Test
    fun testEmbedThrowsExceptionOnEmptyData() = runTest {
        val engine = MockEngine { _ ->
            respond(
                content = emptyDataResponse,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val http = HttpClient(engine) {}
        val client = SiliconFlowLLMClient(apiKey = apiKey, baseClient = http)

        val exception = assertFailsWith<LLMClientException> {
            client.embed(
                text = "Hello, world!",
                model = SiliconFlowModels.Embeddings.BgeLarge_En_V1_5
            )
        }
        assertTrue(exception.message?.contains("Empty data") == true)
    }

    @Test
    fun testEmbedThrowsExceptionOnApiErrorResponse() = runTest {
        val engine = MockEngine { _ ->
            respond(
                content = errorResponse,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val http = HttpClient(engine) {}
        val client = SiliconFlowLLMClient(apiKey = apiKey, baseClient = http)

        val exception = assertFailsWith<LLMClientException> {
            client.embed(
                text = "Hello, world!",
                model = SiliconFlowModels.Embeddings.BgeLarge_En_V1_5
            )
        }
        assertEquals(exception.message?.contains("Invalid API key"), true)
    }

    @Test
    fun testEmbedThrowsExceptionOnHttpError() = runTest {
        val engine = MockEngine { _ ->
            respond(
                content = """{"error": {"message": "Unauthorized"}}""",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val http = HttpClient(engine) {}
        val client = SiliconFlowLLMClient(apiKey = apiKey, baseClient = http)

        assertFailsWith<LLMClientException> {
            client.embed(
                text = "Hello, world!",
                model = SiliconFlowModels.Embeddings.BgeLarge_En_V1_5
            )
        }
    }

    @Test
    fun testEmbedRequestBodyContainsSelectedModel() = runTest {
        var capturedBody = ""

        val engine = MockEngine { req ->
            val textContent = req.body as? TextContent
            capturedBody = textContent?.text ?: ""
            respond(
                content = successfulEmbeddingResponse,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val http = HttpClient(engine) {}
        val client = SiliconFlowLLMClient(apiKey = apiKey, baseClient = http)

        client.embed(
            text = "Test text",
            model = SiliconFlowModels.Embeddings.ProBgeM3
        )

        assertTrue(capturedBody.contains("\"model\":\"Pro/BAAI/bge-m3\""))
    }

    @Test
    fun testEmbedRejectsNonEmbeddingModel() = runTest {
        val engine = MockEngine { _ ->
            respond(
                content = successfulEmbeddingResponse,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val http = HttpClient(engine) {}
        val client = SiliconFlowLLMClient(apiKey = apiKey, baseClient = http)

        assertFailsWith<IllegalArgumentException> {
            client.embed(
                text = "Hello, world!",
                model = SiliconFlowModels.Qwen3_8B
            )
        }
    }
}
