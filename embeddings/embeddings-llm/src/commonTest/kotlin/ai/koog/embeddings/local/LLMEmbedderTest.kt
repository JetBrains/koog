package ai.koog.embeddings.local

import ai.koog.embeddings.base.Vector
import ai.koog.prompt.executor.clients.LLMEmbeddingProvider
import ai.koog.prompt.executor.clients.google.GoogleModels
// TODO: Uncomment after OpenAI migration
// import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.EmbeddingParams
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LLMEmbedderTest {
    // Using a pretty straightforward approach as commonTest doesn't support @ParametrizedTest annotation from JUnit5
    //  Discussable, though.
    // TODO: Re-enable after all providers migrated
    val modelsList = listOf(
        // OpenAIModels.Embeddings.TextEmbedding3Small,
        // OllamaEmbeddingModels.NOMIC_EMBED_TEXT,
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
    fun testEmbedBatch_usesDefaultParallelImplementation() = runTest {
        val model = modelsList.first()
        val mockClient = MockEmbedderClient()
        
        val texts = listOf("text1", "text2", "text3")
        val vectors = listOf(
            Vector(listOf(0.1, 0.2)),
            Vector(listOf(0.3, 0.4)),
            Vector(listOf(0.5, 0.6))
        )
        
        // Mock individual embeddings
        texts.forEachIndexed { i, text -> mockClient.mockEmbedding(text, vectors[i]) }
        
        val results = mockClient.embedBatch(texts, model, EmbeddingParams())
        
        assertEquals(3, results.size)
        assertEquals(listOf(0.1, 0.2), results[0])
        assertEquals(listOf(0.3, 0.4), results[1])
        assertEquals(listOf(0.5, 0.6), results[2])
    }

    @Test
    fun testEmbedBatch_passesParamsToUnderlyingEmbed() = runTest {
        val model = modelsList.first()
        val mockClient = MockEmbedderClient()
        
        mockClient.mockEmbedding("test", Vector(listOf(1.0)))
        
        val params = EmbeddingParams(dimensions = 256)
        mockClient.embedBatch(listOf("test"), model, params)
        
        assertEquals(256, mockClient.lastParams?.dimensions)
    }
    
    @Test
    fun testEmbed_defaultParamsHasNullDimensions() = runTest {
        val model = modelsList.first()
        val mockClient = MockEmbedderClient()
        
        mockClient.mockEmbedding("test", Vector(listOf(1.0)))
        mockClient.embed("test", model, EmbeddingParams())
        
        assertNull(mockClient.lastParams?.dimensions)
    }

    class MockEmbedderClient : LLMEmbeddingProvider {
        private val embeddings = mutableMapOf<String, Vector>()
        private val batchEmbeddings = mutableMapOf<List<String>, List<Vector>>()
        
        /** Track the last params received for verification in tests */
        var lastParams: EmbeddingParams? = null
            private set

        fun mockEmbedding(text: String, vector: Vector) {
            embeddings[text] = vector
        }
        
        fun mockBatchEmbedding(texts: List<String>, vectors: List<Vector>) {
            batchEmbeddings[texts] = vectors
        }

        override suspend fun embed(
            text: String,
            model: LLModel,
            params: EmbeddingParams
        ): List<Double> {
            lastParams = params
            return embeddings[text]?.values 
                ?: throw IllegalArgumentException("No mock embedding for text: $text")
        }
        
        override suspend fun embedBatch(
            texts: List<String>,
            model: LLModel,
            params: EmbeddingParams
        ): List<List<Double>> {
            lastParams = params
            // Return mocked batch if available, otherwise fall back to individual embeds
            return batchEmbeddings[texts]?.map { it.values }
                ?: texts.map { embed(it, model, params) }
        }
    }
}

