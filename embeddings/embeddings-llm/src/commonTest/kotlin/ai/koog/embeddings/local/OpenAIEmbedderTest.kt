package ai.koog.embeddings.local

import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.llm.LLModel
import ai.koog.embeddings.base.Vector
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class OpenAIEmbedderTest {
    @Test
    fun testEmbed() = runTest {
        val mockClient = MockOpenAIEmbedderClient()
        val embedder = LLMEmbedder(mockClient, OpenAIModels.Embeddings.TextEmbedding3Small)

        val text = "Hello, world!"
        val expectedVector = Vector(listOf(0.1, 0.2, 0.3))
        mockClient.mockEmbedding(text, expectedVector)

        val result = embedder.embed(text)
        assertEquals(expectedVector, result)
    }

    @Test
    fun testDiff_identicalVectors() = runTest {
        val mockClient = MockOpenAIEmbedderClient()
        val embedder = LLMEmbedder(mockClient, OpenAIModels.Embeddings.TextEmbedding3Small)

        val vector1 = Vector(listOf(1.0, 2.0, 3.0))
        val vector2 = Vector(listOf(1.0, 2.0, 3.0))

        val result = embedder.diff(vector1, vector2)
        assertEquals(0.0, result, 0.0001)
    }

    @Test
    fun testDiff_differentVectors() = runTest {
        val mockClient = MockOpenAIEmbedderClient()
        val embedder = LLMEmbedder(mockClient, OpenAIModels.Embeddings.TextEmbedding3Small)

        val vector1 = Vector(listOf(1.0, 0.0, 0.0))
        val vector2 = Vector(listOf(0.0, 1.0, 0.0))

        val result = embedder.diff(vector1, vector2)
        assertEquals(1.0, result, 0.0001)
    }

    @Test
    fun testDiff_oppositeVectors() = runTest {
        val mockClient = MockOpenAIEmbedderClient()
        val embedder = LLMEmbedder(mockClient, OpenAIModels.Embeddings.TextEmbedding3Small)

        val vector1 = Vector(listOf(1.0, 2.0, 3.0))
        val vector2 = Vector(listOf(-1.0, -2.0, -3.0))

        val result = embedder.diff(vector1, vector2)
        assertEquals(2.0, result, 0.0001)
    }

    class MockOpenAIEmbedderClient : OpenAILLMClient("") {
        private val embeddings = mutableMapOf<String, Vector>()

        fun mockEmbedding(text: String, vector: Vector) {
            embeddings[text] = vector
        }

        override suspend fun embed(text: String, model: LLModel): List<Double> {
            return embeddings[text]?.values ?: throw IllegalArgumentException("No mock embedding for text: $text")
        }
    }
}
