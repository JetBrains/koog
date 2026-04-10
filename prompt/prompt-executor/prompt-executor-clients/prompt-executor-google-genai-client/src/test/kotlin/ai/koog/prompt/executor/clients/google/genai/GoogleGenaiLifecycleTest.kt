package ai.koog.prompt.executor.clients.google.genai

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.llm.LLMProvider
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GoogleGenaiLifecycleTest {

    private val delegate = mockk<com.google.genai.Client>(relaxed = true)
    private val subject = CustomizedGoogleGenaiLLMClient(delegate)

    @Test
    fun `moderate throws UnsupportedOperationException`() = runTest {
        val error = assertThrows<UnsupportedOperationException> {
            subject.moderate(
                prompt = Prompt(messages = emptyList(), id = "test"),
                model = GoogleModels.Gemini2_5Flash
            )
        }
        error.message shouldContain "Moderation is not supported"
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
