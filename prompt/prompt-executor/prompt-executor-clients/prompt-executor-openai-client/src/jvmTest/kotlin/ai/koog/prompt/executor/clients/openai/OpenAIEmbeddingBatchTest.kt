package ai.koog.prompt.executor.clients.openai

import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.clients.openai.models.OpenAIEmbeddingBatchRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OpenAIEmbeddingBatchTest {

    private val key = "test-key"
    private val model = OpenAIModels.Embeddings.TextEmbedding3Small

    private fun clientReturning(body: String): OpenAILLMClient {
        val engine = MockEngine {
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        return OpenAILLMClient(
            apiKey = key,
            httpClientFactory = KtorKoogHttpClient.Factory(baseClient = HttpClient(engine)),
        )
    }

    @Test
    fun batchRequestSerializesInputAsJsonArray() {
        val request = OpenAIEmbeddingBatchRequest(model = "text-embedding-3-small", input = listOf("a", "b"))
        val jsonString = Json.Default.encodeToString(OpenAIEmbeddingBatchRequest.serializer(), request)
        assertEquals("""{"model":"text-embedding-3-small","input":["a","b"]}""", jsonString)
    }

    @Test
    fun embedReordersResultsByIndex() = runTest {
        // Response intentionally out of order (index 2, 0, 1) to prove sortedBy { index }.
        val body = """
            {
              "data": [
                {"embedding": [0.3, 0.3], "index": 2},
                {"embedding": [0.1, 0.1], "index": 0},
                {"embedding": [0.2, 0.2], "index": 1}
              ],
              "model": "text-embedding-3-small",
              "usage": {"prompt_tokens": 3, "total_tokens": 3}
            }
        """.trimIndent()

        val result = clientReturning(body).embed(listOf("a", "b", "c"), model)

        assertEquals(
            listOf(
                listOf(0.1, 0.1),
                listOf(0.2, 0.2),
                listOf(0.3, 0.3),
            ),
            result,
        )
    }

    @Test
    fun embedThrowsWhenResponseSizeDoesNotMatchInputs() = runTest {
        val body = """
            {
              "data": [{"embedding": [0.1], "index": 0}],
              "model": "text-embedding-3-small"
            }
        """.trimIndent()

        assertFailsWith<IllegalArgumentException> {
            clientReturning(body).embed(listOf("a", "b"), model)
        }
    }

    @Test
    fun embedReturnsEmptyForEmptyInputWithoutCallingApi() = runTest {
        val engine = MockEngine { error("HTTP should not be called for empty input") }
        val client = OpenAILLMClient(
            apiKey = key,
            httpClientFactory = KtorKoogHttpClient.Factory(baseClient = HttpClient(engine)),
        )

        assertEquals(emptyList<List<Double>>(), client.embed(emptyList(), model))
    }
}
