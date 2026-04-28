package ai.koog.integration.tests

import ai.koog.integration.tests.utils.TestCredentials.readTestGoogleAIKeyFromEnv
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.google.GoogleParams
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.message.Message
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class GoogleGroundingLiveTest {

    private val executor = MultiLLMPromptExecutor(GoogleLLMClient(readTestGoogleAIKeyFromEnv()))

    @Test
    fun `grounding enabled returns correct answer for 2026 ICC Cricket World Cup winner`() = runTest(timeout = 60.seconds) {
        val p = prompt("grounding-on-test", params = GoogleParams(groundingEnabled = true)) {
            user("Who won the ICC Cricket World Cup 2026? Answer in one word.")
        }
        val response = executor.execute(p, GoogleModels.Gemini2_5Flash)
        response.shouldNotBeEmpty()
        val content = response.first().shouldBeInstanceOf<Message.Assistant>().content
        content.shouldNotBeBlank()
        content.lowercase() shouldContain "india"
    }

    @Test
    fun `grounding disabled answers from training data`() = runTest(timeout = 60.seconds) {
        val p = prompt("grounding-off-test", params = GoogleParams(groundingEnabled = false)) {
            user("What is the capital of France? Answer in one word.")
        }
        val response = executor.execute(p, GoogleModels.Gemini2_5Flash)
        response.shouldNotBeEmpty()
        val content = response.first().shouldBeInstanceOf<Message.Assistant>().content
        content.shouldNotBeBlank()
        content.lowercase() shouldContain "paris"
    }
}
