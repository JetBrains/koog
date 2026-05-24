package ai.koog.prompt.executor.ollama.client

import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.ollama.client.dto.EmbeddingBatchRequestDTO
import ai.koog.prompt.executor.ollama.client.dto.EmbeddingRequestDTO
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class OllamaEmbeddingClientTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun `embed one string supports current api embed response`() = runTest {
        var requestPath: String? = null
        var requestBody: EmbeddingRequestDTO? = null

        val mockEngine = MockEngine { request ->
            requestPath = request.url.encodedPath.removePrefix("/")
            requestBody = request.extractSingleEmbeddingRequestBody()
            respond(
                """
                {
                  "model": "nomic-embed-text",
                  "embeddings": [[0.1, 0.2, 0.3]]
                }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()))
            )
        }
        val client = OllamaClient(httpClientFactory = KtorKoogHttpClient.Factory(HttpClient(mockEngine)))

        val embedding = client.embed("sample", OllamaModels.Embeddings.NOMIC_EMBED_TEXT)

        assertEquals("api/embed", requestPath)
        assertEquals(EmbeddingRequestDTO(model = "nomic-embed-text", input = "sample"), requestBody)
        assertEquals(listOf(0.1, 0.2, 0.3), embedding)
    }

    @Test
    fun `embed one string supports legacy api embeddings response`() = runTest {
        val mockEngine = MockEngine {
            respond(
                """
                {
                  "embedding": [0.4, 0.5, 0.6]
                }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()))
            )
        }
        val client = OllamaClient(httpClientFactory = KtorKoogHttpClient.Factory(HttpClient(mockEngine)))

        val embedding = client.embed("sample", OllamaModels.Embeddings.NOMIC_EMBED_TEXT)

        assertEquals(listOf(0.4, 0.5, 0.6), embedding)
    }

    @Test
    fun `embed list keeps batch response shape`() = runTest {
        var requestBody: EmbeddingBatchRequestDTO? = null

        val mockEngine = MockEngine { request ->
            requestBody = request.extractBatchEmbeddingRequestBody()
            respond(
                """
                {
                  "model": "nomic-embed-text",
                  "embeddings": [[1.0, 2.0], [3.0, 4.0]]
                }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()))
            )
        }
        val client = OllamaClient(httpClientFactory = KtorKoogHttpClient.Factory(HttpClient(mockEngine)))

        val embeddings = client.embed(listOf("one", "two"), OllamaModels.Embeddings.NOMIC_EMBED_TEXT)

        assertEquals(
            EmbeddingBatchRequestDTO(model = "nomic-embed-text", input = listOf("one", "two")),
            requestBody
        )
        assertEquals(listOf(listOf(1.0, 2.0), listOf(3.0, 4.0)), embeddings)
    }

    private fun HttpRequestData.extractSingleEmbeddingRequestBody(): EmbeddingRequestDTO {
        val requestContent = body as TextContent
        return json.decodeFromString(requestContent.text)
    }

    private fun HttpRequestData.extractBatchEmbeddingRequestBody(): EmbeddingBatchRequestDTO {
        val requestContent = body as TextContent
        return json.decodeFromString(requestContent.text)
    }
}
