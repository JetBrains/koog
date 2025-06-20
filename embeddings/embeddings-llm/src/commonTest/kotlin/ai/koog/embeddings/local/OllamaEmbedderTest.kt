package ai.koog.embeddings.local

import ai.koog.embeddings.base.Vector
import ai.koog.prompt.executor.clients.LLMEmbeddingProvider
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class OllamaEmbedderTest {
    @Test
    fun testEmbed() = runTest {
        val mockClient = MockOllamaEmbedderClient()
        val embedder = LLMEmbedder(mockClient, OllamaEmbeddingModels.NOMIC_EMBED_TEXT)

        val text = "Hello, world!"
        val expectedVector = Vector(listOf(0.1, 0.2, 0.3))
        mockClient.mockEmbedding(text, expectedVector)

        val result = embedder.embed(text)
        assertEquals(expectedVector, result)
    }

    @Test
    fun testDiff_identicalVectors() = runTest {
        val mockClient = MockOllamaEmbedderClient()
        val embedder = LLMEmbedder(mockClient, OllamaEmbeddingModels.NOMIC_EMBED_TEXT)

        val vector1 = Vector(listOf(1.0, 2.0, 3.0))
        val vector2 = Vector(listOf(1.0, 2.0, 3.0))

        val result = embedder.diff(vector1, vector2)
        assertEquals(0.0, result, 0.0001)
    }

    @Test
    fun testDiff_differentVectors() = runTest {
        val mockClient = MockOllamaEmbedderClient()
        val embedder = LLMEmbedder(mockClient, OllamaEmbeddingModels.NOMIC_EMBED_TEXT)

        val vector1 = Vector(listOf(1.0, 0.0, 0.0))
        val vector2 = Vector(listOf(0.0, 1.0, 0.0))

        val result = embedder.diff(vector1, vector2)
        assertEquals(1.0, result, 0.0001)
    }

    @Test
    fun testDiff_oppositeVectors() = runTest {
        val mockClient = MockOllamaEmbedderClient()
        val embedder = LLMEmbedder(mockClient, OllamaEmbeddingModels.NOMIC_EMBED_TEXT)

        val vector1 = Vector(listOf(1.0, 2.0, 3.0))
        val vector2 = Vector(listOf(-1.0, -2.0, -3.0))

        val result = embedder.diff(vector1, vector2)
        assertEquals(2.0, result, 0.0001)
    }
}

class MockOllamaEmbedderClient : LLMEmbeddingProvider {
    private val embeddings = mutableMapOf<String, Vector>()

    fun mockEmbedding(text: String, vector: Vector) {
        embeddings[text] = vector
    }

    override suspend fun embed(text: String, model: LLModel): List<Double> {
        require(model.provider == LLMProvider.Ollama) { "Model not supported by Ollama" }
        return embeddings[text]?.values ?: throw IllegalArgumentException("No mock embedding for text: $text")
    }
}
