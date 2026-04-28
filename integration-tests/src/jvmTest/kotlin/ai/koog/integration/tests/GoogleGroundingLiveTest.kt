package ai.koog.integration.tests

import ai.koog.integration.tests.utils.TestCredentials.readTestGoogleAIKeyFromEnv
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.google.GoogleParams
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.message.Message
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class GoogleGroundingLiveTest {

    private val client = GoogleLLMClient(readTestGoogleAIKeyFromEnv())
    private val executor = SingleLLMPromptExecutor(client)

    @Test
    fun `grounding returns non-empty response for Gemini 2_5 Flash`() = runTest(timeout = 60.seconds) {
        val p = prompt("grounding-test", params = GoogleParams(groundingEnabled = true)) {
            user("Iran vs US war 2026, what is happening?")
        }
        val response = executor.execute(p, GoogleModels.Gemini2_5Flash)
        response.shouldNotBeEmpty()
        response.first().shouldBeInstanceOf<Message.Assistant>()
        check((response.first() as Message.Assistant).content.isNotBlank())
    }

    @Test
    fun `grounding with disabled flag returns response without search`() = runTest(timeout = 60.seconds) {
        val p = prompt("grounding-off-test", params = GoogleParams(groundingEnabled = false)) {
            user("What is the capital of France?")
        }
        val response = executor.execute(p, GoogleModels.Gemini2_5Flash)
        response.shouldNotBeEmpty()
        response.first().shouldBeInstanceOf<Message.Assistant>()
    }
}
