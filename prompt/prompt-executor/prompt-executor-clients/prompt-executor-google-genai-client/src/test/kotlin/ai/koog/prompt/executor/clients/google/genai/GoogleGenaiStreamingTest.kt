package ai.koog.prompt.executor.clients.google.genai

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import io.kotest.matchers.string.shouldContain
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Streaming tests are limited to capability validation. The streaming body (chunk iteration,
 * emitToolCallDelta, emitTextDelta, emitEnd) cannot be unit-tested because [com.google.genai.Client]
 * and its `async.models` field chain are final Java classes that require reflection-based wiring.
 * Streaming behavior is validated through integration tests with a real API key.
 */
class GoogleGenaiStreamingTest {

    private val delegate = mockk<com.google.genai.Client>(relaxed = true)
    private val subject = CustomizedGoogleGenaiLLMClient(delegate, models = TestModels.all)

    @Test
    fun `executeStreaming validates Completion capability`() = runTest {
        val error = assertThrows<IllegalArgumentException> {
            subject.executeStreaming(
                prompt = Prompt(
                    messages = listOf(Message.User("Hello", RequestMetaInfo.Empty)),
                    id = "test"
                ),
                model = TestModels.noCap
            ).collect {}
        }
        error.message shouldContain "does not support chat completions"
    }
}
