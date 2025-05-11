package ai.jetbrains.code.prompt.executor.clients.openrouter

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OpenRouterEmbeddingTest {

    // API key for testing
    private val apiKey: String = readTestOpenRouterKeyFromEnv()
    
    // Free model for testing
    private val testModel = OpenRouterModels.Phi4Reasoning

    @Disabled("This test is ignored because it requires a valid API key.")
    @Test
    fun testEmbed() = runTest {
        val client = OpenRouterLLMClient(apiKey)
        
        val text = "This is a test text for embedding."
        val embedding = client.embed(text, testModel)
        
        // Verify the embedding is not null and has the expected structure
        assertNotNull(embedding)
        assertTrue(embedding.isNotEmpty(), "Embedding should not be empty")
        
        // Check that the embedding has a reasonable number of dimensions
        assertTrue(embedding.size > 100, "Embedding should have a reasonable number of dimensions")
        
        // Check that the embedding values are within a reasonable range
        embedding.forEach { value ->
            assertTrue(value.isFinite(), "Embedding values should be finite")
        }
    }
}