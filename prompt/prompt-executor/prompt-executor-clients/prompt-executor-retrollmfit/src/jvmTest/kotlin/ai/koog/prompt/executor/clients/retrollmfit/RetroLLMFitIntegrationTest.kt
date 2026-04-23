package ai.koog.prompt.executor.clients.retrollmfit

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.message.Message
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock

// Jaika server shapes

@LLMEndpoint(
    url = "https://35-207-202-131.sslip.io/api/prompt",
    authHeaderName = "X-User-Id",
    authHeaderValue = "116542085266142929154",
)
@Serializable
data class JaikaRequest(@PromptField val prompt: String, val stream: Boolean = false)

@Serializable
data class JaikaResponse(
    @ResponseTextField val text: String,
    val session_id: String = "",
    val type: String = "text",
)

/**
 * Integration test hitting the live Jaika server.
 * Demonstrates that RetroLLMFit needs zero handwritten HTTP or client code —
 * just annotated data classes.
 */
class RetroLLMFitIntegrationTest {

    @Test
    fun testJaikaRealCall() = runTest {
        val client = RetroLLMFit.create<JaikaRequest, JaikaResponse>()

        val prompt = Prompt.build(id = "jaika-test", clock = Clock.System) {
            user("Hello! Can you introduce yourself in one sentence?")
        }

        val responses = client.execute(prompt, RetroLLMFitModel)

        assertTrue(responses.isNotEmpty(), "Expected at least one response")
        val msg = assertIs<Message.Assistant>(responses.first())
        assertTrue(msg.content.isNotBlank(), "Expected non-blank response text, got: '${msg.content}'")

        println("Jaika replied: ${msg.content}")
    }
}
