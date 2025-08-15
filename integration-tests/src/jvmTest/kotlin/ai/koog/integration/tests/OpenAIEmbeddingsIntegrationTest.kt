package ai.koog.integration.tests

import ai.koog.integration.tests.utils.Models
import ai.koog.integration.tests.utils.TestUtils.readTestOpenAIKeyFromEnv
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.llm.LLMProvider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OpenAIEmbeddingsIntegrationTest {

    // API key for testing
    private val apiKey: String get() = readTestOpenAIKeyFromEnv()

    @Test
    fun integration_testEmbed() = runTest {
        Models.assumeAvailable(LLMProvider.OpenAI)
        val client = OpenAILLMClient(apiKey)

        val text = "This is a test text for embedding."
        val embedding = client.embed(text, OpenAIModels.Embeddings.TextEmbedding3Small)

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

    @Test
    fun integration_testEmbedWithCustomModel() = runTest {
        Models.assumeAvailable(LLMProvider.OpenAI)
        val client = OpenAILLMClient(apiKey)

        val text = "This is a test text for embedding with a custom model."
        val embedding = client.embed(text, OpenAIModels.Embeddings.TextEmbedding3Large)

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
