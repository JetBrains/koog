package ai.koog.spring.ai.embedding

import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.ai.document.Document
import org.springframework.ai.embedding.Embedding
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.embedding.EmbeddingRequest
import org.springframework.ai.embedding.EmbeddingResponse
import org.springframework.ai.embedding.EmbeddingResultMetadata

class SpringAiLLMEmbeddingProviderTest {

    private val testModel = LLModel(
        provider = LLMProvider.Ollama,
        id = "test-embedding-model",
        capabilities = listOf(LLMCapability.Temperature),
        contextLength = 4096
    )

    private fun embeddingModel(result: FloatArray): EmbeddingModel = object : EmbeddingModel {
        override fun embed(text: String): FloatArray = throw UnsupportedOperationException()
        override fun embed(document: Document): FloatArray = throw UnsupportedOperationException()
        override fun call(request: EmbeddingRequest): EmbeddingResponse =
            EmbeddingResponse(listOf(Embedding(result, 0, EmbeddingResultMetadata())))
    }

    @Test
    fun testEmbedReturnsListOfDoublesFromEmbeddingModel() = runBlocking {
        val floats = floatArrayOf(0.1f, 0.2f, 0.3f)
        val provider = SpringAiLLMEmbeddingProvider.builder().embeddingModel(embeddingModel(floats)).build()

        val result = provider.embed("hello", testModel)

        assertEquals(3, result.size)
        assertEquals(0.1f.toDouble(), result[0])
        assertEquals(0.2f.toDouble(), result[1])
        assertEquals(0.3f.toDouble(), result[2])
    }

    @Test
    fun testEmbedReturnsEmptyListForEmptyEmbedding() = runBlocking {
        val provider = SpringAiLLMEmbeddingProvider.builder().embeddingModel(embeddingModel(floatArrayOf())).build()

        val result = provider.embed("hello", testModel)

        assertEquals(0, result.size)
    }

    @Test
    fun testEmbedPassesTextToEmbeddingModel() = runBlocking {
        var capturedText: String? = null
        val model = object : EmbeddingModel {
            override fun embed(text: String): FloatArray = throw UnsupportedOperationException()
            override fun embed(document: Document): FloatArray = throw UnsupportedOperationException()
            override fun call(request: EmbeddingRequest): EmbeddingResponse {
                capturedText = request.instructions.firstOrNull()
                return EmbeddingResponse(listOf(Embedding(floatArrayOf(1.0f), 0, EmbeddingResultMetadata())))
            }
        }
        val provider = SpringAiLLMEmbeddingProvider.builder().embeddingModel(model).build()

        provider.embed("test input", testModel)

        assertEquals("test input", capturedText)
    }

    @Test
    fun testEmbedWrapsExceptionFromEmbeddingModelInLLMClientException(): Unit = runBlocking {
        val model = object : EmbeddingModel {
            override fun embed(text: String): FloatArray = throw UnsupportedOperationException()
            override fun embed(document: Document): FloatArray = throw UnsupportedOperationException()
            override fun call(request: EmbeddingRequest): EmbeddingResponse =
                throw RuntimeException("Model error")
        }
        val provider = SpringAiLLMEmbeddingProvider.builder().embeddingModel(model).build()

        val exception = assertThrows<LLMClientException> {
            provider.embed("hello", testModel)
        }
        assertTrue(exception.message!!.contains("spring-ai-embedding"))
        assertTrue(exception.message!!.contains("Model error"))
        assertTrue(exception.cause is RuntimeException)
    }
}
