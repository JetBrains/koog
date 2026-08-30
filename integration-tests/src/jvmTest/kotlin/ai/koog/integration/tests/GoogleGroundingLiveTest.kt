package ai.koog.integration.tests

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.google.GoogleParams
import ai.koog.prompt.executor.clients.google.GoogleSearchConfig
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.message.Message
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.time.Duration.Companion.seconds

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GoogleGroundingLiveTest {

    private lateinit var executor: MultiLLMPromptExecutor

    @BeforeAll
    fun setup() {
        val apiKey = System.getenv("GEMINI_API_TEST_KEY")
        assumeTrue(apiKey != null, "GEMINI_API_TEST_KEY not set — skipping live grounding tests")
        executor = MultiLLMPromptExecutor(GoogleLLMClient(apiKey!!))
    }

    @Test
    fun `grounding enabled returns correct answer for 2026 ICC Cricket World Cup winner`() = runTest(timeout = 60.seconds) {
        val p = prompt(
            "grounding-on-test",
            params = GoogleParams(groundingSearchConfig = GoogleSearchConfig(groundingEnabled = true))
        ) {
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
        val p = prompt("grounding-off-test", params = GoogleParams()) {
            user("What is the capital of France? Answer in one word.")
        }
        val response = executor.execute(p, GoogleModels.Gemini2_5Flash)
        response.shouldNotBeEmpty()
        val content = response.first().shouldBeInstanceOf<Message.Assistant>().content
        content.shouldNotBeBlank()
        content.lowercase() shouldContain "paris"
    }
}
