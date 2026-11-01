package ai.koog.embeddings.local

import ai.koog.embeddings.base.Vector
import ai.koog.prompt.executor.clients.LLMEmbeddingProvider
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.prompt.llm.LLModel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LLMEmbedderTest {
    // Using a pretty straightforward approach as commonTest doesn't support @ParametrizedTest annotation from JUnit5
    //  Discussable, though.
    val modelsList = listOf(
        OpenAIModels.Embeddings.TextEmbedding3Small,
        OllamaModels.Embeddings.NOMIC_EMBED_TEXT,
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

    @Test
    fun testEmbedBatch_delegatesToClientBatch() = runTest {
        val model = OpenAIModels.Embeddings.TextEmbedding3Small
        val client = MockEmbedderClient()
        val embedder = LLMEmbedder(client, model)

        val vA = Vector(listOf(0.1, 0.1))
        val vB = Vector(listOf(0.2, 0.2))
        val vC = Vector(listOf(0.3, 0.3))
        client.mockEmbedding("a", vA)
        client.mockEmbedding("b", vB)
        client.mockEmbedding("c", vC)

        val result = embedder.embed(listOf("a", "b", "c"))

        assertEquals(listOf(vA, vB, vC), result)
        assertEquals(1, client.batchCalls, "should issue a single batch call")
        assertEquals(0, client.singleCalls, "should not loop single calls")
    }

    @Test
    fun testEmbedBatch_propagatesUnsupportedFromClient() = runTest {
        val model = OpenAIModels.Embeddings.TextEmbedding3Small
        val client = MockEmbedderClient(batchSupported = false)
        val embedder = LLMEmbedder(client, model)

        assertFailsWith<UnsupportedOperationException> {
            embedder.embed(listOf("a", "b"))
        }
    }

    @Test
    fun testEmbedBatch_emptyReturnsEmptyWithoutCallingClient() = runTest {
        val model = OpenAIModels.Embeddings.TextEmbedding3Small
        val client = MockEmbedderClient(batchSupported = false)
        val embedder = LLMEmbedder(client, model)

        val result = embedder.embed(emptyList())

        assertEquals(emptyList<Vector>(), result)
        assertEquals(0, client.batchCalls)
        assertEquals(0, client.singleCalls)
    }

    class MockEmbedderClient(
        private val batchSupported: Boolean = true
    ) : LLMEmbeddingProvider() {
        private val embeddings = mutableMapOf<String, Vector>()

        var singleCalls: Int = 0
            private set
        var batchCalls: Int = 0
            private set

        fun mockEmbedding(text: String, vector: Vector) {
            embeddings[text] = vector
        }

        private fun lookup(text: String): List<Double> =
            embeddings[text]?.values ?: throw IllegalArgumentException("No mock embedding for text: $text")

        override suspend fun embed(text: String, model: LLModel): List<Double> {
            singleCalls++
            return lookup(text)
        }

        override suspend fun embed(
            inputs: List<String>,
            model: LLModel
        ): List<List<Double>> {
            if (!batchSupported) {
                throw UnsupportedOperationException("Batch embedding is not supported by this mock provider.")
            }
            batchCalls++
            return inputs.map { lookup(it) }
        }
    }
}
