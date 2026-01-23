package ai.koog.prompt.executor.clients.openrouter

import ai.koog.prompt.executor.clients.LLMClientException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OpenRouterLLMClientEmbeddingTest {

    private val apiKey = "test-api-key"

    //language=json
    private val successfulEmbeddingResponse = """
        {
            "data": [{"embedding": [0.1, 0.2, 0.3, 0.4, 0.5], "index": 0}],
            "model": "openai/text-embedding-3-small",
            "usage": {"prompt_tokens": 5, "total_tokens": 5}
        }
    """.trimIndent()

    //language=json
    private val emptyDataResponse = """
        {
            "data": [],
            "model": "openai/text-embedding-3-small"
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
    fun `embed returns embedding vector on success`() = runTest {
        var capturedUrl = ""
        var capturedMethod: HttpMethod? = null
        var capturedAuth: String? = null

        val engine = MockEngine.Companion { req ->
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
        val client = OpenRouterLLMClient(apiKey = apiKey, baseClient = http)

        val embedding = client.embed(
            text = "Hello, world!",
            model = OpenRouterModels.Embeddings.OpenAITextEmbedding3Small
        )

        assertTrue(capturedUrl.startsWith("https://openrouter.ai/"))
        assertTrue(capturedUrl.endsWith("api/v1/embeddings"))
        assertEquals(HttpMethod.Post, capturedMethod)
        assertEquals("Bearer $apiKey", capturedAuth)
        assertEquals(listOf(0.1, 0.2, 0.3, 0.4, 0.5), embedding)
    }

    @Test
    fun `embed throws exception on empty data`() = runTest {
        val engine = MockEngine.Companion { _ ->
            respond(
                content = emptyDataResponse,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val http = HttpClient(engine) {}
        val client = OpenRouterLLMClient(apiKey = apiKey, baseClient = http)

        val exception = assertFailsWith<LLMClientException> {
            client.embed(
                text = "Hello, world!",
                model = OpenRouterModels.Embeddings.OpenAITextEmbedding3Small
            )
        }
        assertTrue(exception.message?.contains("Empty data") == true)
    }

    @Test
    fun `embed throws exception on API error response`() = runTest {
        val engine = MockEngine.Companion { _ ->
            respond(
                content = errorResponse,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val http = HttpClient(engine) {}
        val client = OpenRouterLLMClient(apiKey = apiKey, baseClient = http)

        val exception = assertFailsWith<LLMClientException> {
            client.embed(
                text = "Hello, world!",
                model = OpenRouterModels.Embeddings.OpenAITextEmbedding3Small
            )
        }
        assertTrue(exception.message?.contains("Invalid API key") == true)
    }

    @Test
    fun `embed throws exception on HTTP error`() = runTest {
        val engine = MockEngine.Companion { _ ->
            respond(
                content = """{"error": {"message": "Unauthorized"}}""",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val http = HttpClient(engine) {}
        val client = OpenRouterLLMClient(apiKey = apiKey, baseClient = http)

        assertFailsWith<LLMClientException> {
            client.embed(
                text = "Hello, world!",
                model = OpenRouterModels.Embeddings.OpenAITextEmbedding3Small
            )
        }
    }

    @Test
    fun `embed uses custom settings`() = runTest {
        var capturedUrl = ""

        val engine = MockEngine.Companion { req ->
            capturedUrl = req.url.toString()
            respond(
                content = successfulEmbeddingResponse,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val http = HttpClient(engine) {}
        val customSettings = OpenRouterClientSettings(
            baseUrl = "https://custom.openrouter.ai",
            embeddingsPath = "custom/embeddings"
        )
        val client = OpenRouterLLMClient(apiKey = apiKey, settings = customSettings, baseClient = http)

        client.embed(
            text = "Hello, world!",
            model = OpenRouterModels.Embeddings.OpenAITextEmbedding3Small
        )

        assertTrue(capturedUrl.startsWith("https://custom.openrouter.ai/"))
        assertTrue(capturedUrl.endsWith("custom/embeddings"))
    }

    @Test
    fun `embed works with different embedding models`() = runTest {
        var capturedBody = ""

        val engine = MockEngine.Companion { req ->
            capturedBody = req.body.toString()
            respond(
                content = successfulEmbeddingResponse,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val http = HttpClient(engine) {}
        val client = OpenRouterLLMClient(apiKey = apiKey, baseClient = http)

        // Test with a different model
        client.embed(
            text = "Test text",
            model = OpenRouterModels.Embeddings.ThenlperGteBase
        )

        assertTrue(capturedBody.contains("thenlper/gte-base"))
    }
}
