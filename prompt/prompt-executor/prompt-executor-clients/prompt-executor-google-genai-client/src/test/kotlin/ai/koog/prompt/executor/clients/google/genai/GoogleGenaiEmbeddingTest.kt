package ai.koog.prompt.executor.clients.google.genai

import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import com.google.genai.types.ContentEmbedding
import com.google.genai.types.EmbedContentResponse
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GoogleGenaiEmbeddingTest {

    private val delegate = mockk<com.google.genai.Client>(relaxed = true)
    private val subject = CustomizedGoogleGenaiLLMClient(delegate)

    // region Scenario: embedding response extraction

    @Test
    fun `embedding response with 3 values is extracted as List of Double`() {
        val response = EmbedContentResponse.builder()
            .embeddings(
                listOf(
                    ContentEmbedding.builder().values(listOf(0.1f, 0.2f, 0.3f)).build()
                )
            )
            .build()

        val values = response.embeddings().get()
            .first().values().get()
            .map { it.toDouble() }

        values shouldHaveSize 3
        values[0] shouldBe 0.1f.toDouble()
        values[1] shouldBe 0.2f.toDouble()
        values[2] shouldBe 0.3f.toDouble()
    }

    @Test
    fun `embedding response with empty embeddings list returns empty`() {
        val response = EmbedContentResponse.builder().embeddings(emptyList()).build()

        val values = response.embeddings().get()
            .firstOrNull()?.values()?.orElse(emptyList())
            ?.map { it.toDouble() } ?: emptyList()

        values shouldHaveSize 0
    }

    @Test
    fun `embedding response with high-dimensional vector preserves all values`() {
        val floats = (1..768).map { it.toFloat() / 1000f }
        val response = EmbedContentResponse.builder()
            .embeddings(listOf(ContentEmbedding.builder().values(floats).build()))
            .build()

        val values = response.embeddings().get()
            .first().values().get()
            .map { it.toDouble() }

        values shouldHaveSize 768
        values[0] shouldBe (1f / 1000f).toDouble()
        values[767] shouldBe (768f / 1000f).toDouble()
    }

    // endregion

    // region Capability validation

    @Test
    fun `embed rejects model without Embed capability`() = runTest {
        val model =
            LLModel(
                provider = LLMProvider.Google,
                id = "no-embed",
                capabilities = listOf(LLMCapability.Completion)
            )
        assertThrows<IllegalArgumentException> { subject.embed("hello", model) }
    }

    // endregion
}
