package ai.koog.prompt.executor.clients.google.genai

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GoogleGenaiLifecycleTest {

    private val delegate = mockk<com.google.genai.Client>(relaxed = true)
    private val subject = CustomizedGoogleGenaiLLMClient(delegate)

    @Test
    fun `models returns default GoogleModels list with known models`() = runTest {
        val models = subject.models()

        models.isNotEmpty() shouldBe true
        models shouldContain GoogleModels.Gemini2_5Flash
        models shouldContain GoogleModels.Gemini2_5Pro
        models shouldContain GoogleModels.Gemini3_Pro_Preview
        models shouldContain GoogleModels.Gemini3_Flash_Preview
        models shouldContain GoogleModels.Embeddings.GeminiEmbedding001
    }

    @Test
    fun `models returns custom list when provided via constructor`() = runTest {
        val customModels = listOf(
            LLModel(provider = LLMProvider.Google, id = "custom-model", capabilities = listOf(LLMCapability.Completion))
        )
        val customClient = GoogleGenaiLLMClient(delegate, models = customModels)

        customClient.models() shouldContainExactly customModels
    }

    @Test
    fun `moderate throws UnsupportedOperationException`() = runTest {
        assertThrows<UnsupportedOperationException> {
            subject.moderate(
                prompt = Prompt(messages = emptyList(), id = "test"),
                model = GoogleModels.Gemini2_5Flash
            )
        }
    }

    @Test
    fun `close delegates to client`() {
        val mockClient = mockk<com.google.genai.Client>(relaxed = true)
        val llmClient = GoogleGenaiLLMClient(mockClient)

        llmClient.close()

        verify { mockClient.close() }
    }

    @Test
    fun `llmProvider returns Google`() {
        subject.llmProvider() shouldBe LLMProvider.Google
    }
}
