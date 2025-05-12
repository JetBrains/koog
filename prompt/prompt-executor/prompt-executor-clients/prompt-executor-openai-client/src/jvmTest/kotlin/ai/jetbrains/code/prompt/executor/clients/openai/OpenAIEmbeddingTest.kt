package ai.jetbrains.code.prompt.executor.clients.openai

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OpenAIEmbeddingTest {

    // API key for testing
    private val apiKey: String = readTestOpenAIKeyFromEnv()

    @Disabled("TODO: pass the `OPENAI_API_TEST_KEY`")
    @Test
    fun testEmbed() = runTest {
        val client = OpenAILLMClient(apiKey)

        val text = "This is a test text for embedding."
        val embedding = client.embed(text, OpenAIModels.Embeddings.TextEmbeddingAda3Small)

        // Verify the embedding is not null and has the expected structure
        assertNotNull(embedding)
        assertTrue(embedding.isNotEmpty(), "Embedding should not be empty")

        // OpenAI embeddings typically have 1536 dimensions for text-embedding-ada-002
        // But we'll just check that it has a reasonable number of dimensions
        assertTrue(embedding.size > 100, "Embedding should have a reasonable number of dimensions")

        // Check that the embedding values are within a reasonable range
        embedding.forEach { value ->
            assertTrue(value.isFinite(), "Embedding values should be finite")
        }

        println("Embedding: $embedding")
    }

    @Disabled("TODO: pass the `OPENAI_API_TEST_KEY`")
    @Test
    fun testEmbedWithCustomModel() = runTest {
        val client = OpenAILLMClient(apiKey)

        val text = "This is a test text for embedding with a custom model."
        val embedding = client.embed(text, OpenAIModels.Embeddings.TextEmbeddingAda3Large)

        // Verify the embedding is not null and has the expected structure
        assertNotNull(embedding)
        assertTrue(embedding.isNotEmpty(), "Embedding should not be empty")

        // Check that the embedding values are within a reasonable range
        embedding.forEach { value ->
            assertTrue(value.isFinite(), "Embedding values should be finite")
        }

        println("Embedding: $embedding")
    }
}