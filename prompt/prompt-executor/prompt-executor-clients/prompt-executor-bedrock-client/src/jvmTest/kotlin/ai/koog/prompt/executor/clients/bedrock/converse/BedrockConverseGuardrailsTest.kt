package ai.koog.prompt.executor.clients.bedrock.converse

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.bedrock.BedrockGuardrailsSettings
import ai.koog.prompt.executor.clients.bedrock.BedrockModels
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BedrockConverseGuardrailsTest {

    private val model = BedrockModels.AnthropicClaude4Sonnet

    private val guardrailSettings = BedrockGuardrailsSettings(
        guardrailIdentifier = "test-guardrail-id",
        guardrailVersion = "2"
    )

    @Test
    fun testConverseRequestWithGuardrailSettings() {
        val prompt = Prompt.build("test") { user("Hello") }
        val request = BedrockConverseConverters.createConverseRequest(prompt, model, emptyList(), guardrailSettings)

        val config = assertNotNull(request.guardrailConfig)
        assertEquals("test-guardrail-id", config.guardrailIdentifier)
        assertEquals("2", config.guardrailVersion)
    }

    @Test
    fun testConverseRequestWithoutGuardrailSettings() {
        val prompt = Prompt.build("test") { user("Hello") }
        val request = BedrockConverseConverters.createConverseRequest(prompt, model, emptyList())

        assertNull(request.guardrailConfig)
    }

    @Test
    fun testConverseStreamRequestWithGuardrailSettings() {
        val prompt = Prompt.build("test") { user("Hello") }
        val request = BedrockConverseConverters.createConverseStreamRequest(prompt, model, emptyList(), guardrailSettings)

        val config = assertNotNull(request.guardrailConfig)
        assertEquals("test-guardrail-id", config.guardrailIdentifier)
        assertEquals("2", config.guardrailVersion)
    }

    @Test
    fun testConverseStreamRequestWithoutGuardrailSettings() {
        val prompt = Prompt.build("test") { user("Hello") }
        val request = BedrockConverseConverters.createConverseStreamRequest(prompt, model, emptyList())

        assertNull(request.guardrailConfig)
    }
}
