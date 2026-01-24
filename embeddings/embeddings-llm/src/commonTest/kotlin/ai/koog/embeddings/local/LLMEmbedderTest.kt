package ai.koog.embeddings.local

import ai.koog.embeddings.base.Vector
import ai.koog.prompt.executor.clients.LLMEmbeddingProvider
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.llm.LLModel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LLMEmbedderTest {
    // Using a pretty straightforward approach as commonTest doesn't support @ParametrizedTest annotation from JUnit5
    //  Discussable, though.
    val modelsList = listOf(
        OpenAIModels.Embeddings.TextEmbedding3Small,
        OllamaEmbeddingModels.NOMIC_EMBED_TEXT,
        GoogleModels.Embeddings.GeminiEmbedding001,
    )

    @Test
    fun testEmbed() = runTest {
        for (model in modelsList) {
            val mockClient = MockEmbedderClient()
            val embedder = LLMEmbedder(mockClient, model)

            val text = "Hello, world!"
            val expectedVector = Vector(listOf(0.1, 0.2, 0.3))
            mockClient.mockEmbedding(text, expectedVector)

            val result = embedder.embed(text)
            assertEquals(expectedVector, result, "Embedding for model $model failed")
        }
    }

    @Test
    fun testDiff_identicalVectors() = runTest {
        for (model in modelsList) {
            val mockClient = MockEmbedderClient()
            val embedder = LLMEmbedder(mockClient, model)

            val vector1 = Vector(listOf(1.0, 2.0, 3.0))
            val vector2 = Vector(listOf(1.0, 2.0, 3.0))

            val result = embedder.diff(vector1, vector2)
            assertEquals(0.0, result, 0.0001, "Embedding for model $model failed")
        }
    }

    @Test
    fun testDiff_differentVectors() = runTest {
        for (model in modelsList) {
            val mockClient = MockEmbedderClient()
            val embedder = LLMEmbedder(mockClient, model)

            val vector1 = Vector(listOf(1.0, 0.0, 0.0))
            val vector2 = Vector(listOf(0.0, 1.0, 0.0))

            val result = embedder.diff(vector1, vector2)
            assertEquals(1.0, result, 0.0001, "Embedding for model $model failed")
        }
    }

    @Test
    fun testDiff_oppositeVectors() = runTest {
        for (model in modelsList) {
            val mockClient = MockEmbedderClient()
            val embedder = LLMEmbedder(mockClient, model)

            val vector1 = Vector(listOf(1.0, 2.0, 3.0))
            val vector2 = Vector(listOf(-1.0, -2.0, -3.0))

            val result = embedder.diff(vector1, vector2)
            assertEquals(2.0, result, 0.0001, "Embedding for model $model failed")
        }
    }

    class MockEmbedderClient : LLMEmbeddingProvider {
        private val embeddings = mutableMapOf<String, Vector>()

        fun mockEmbedding(text: String, vector: Vector) {
            embeddings[text] = vector
        }

        override suspend fun embed(text: String, model: LLModel): List<Double> {
            return embeddings[text]?.values ?: throw IllegalArgumentException("No mock embedding for text: $text")
        }
    }
}
