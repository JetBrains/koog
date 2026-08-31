package ai.koog.prompt.executor.clients.anthropic

import ai.koog.prompt.Prompt
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Anthropic dropped the sampling parameters with the 5 generation: `temperature`, `top_p` and `top_k`
 * are rejected with HTTP 400. Models of that generation therefore do not declare
 * [LLMCapability.Temperature], and the client must not put `temperature` into the request body for them,
 * even when the caller configured one in [AnthropicParams].
 */
class AnthropicTemperatureSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = AnthropicLLMClient(apiKey = "test-key")

    private fun requestBodyFor(model: LLModel): Map<String, JsonElement> {
        val prompt = Prompt.build("test", params = AnthropicParams(temperature = 0.7)) {
            user("Hello")
        }
        return json.parseToJsonElement(client.createAnthropicRequest(prompt, emptyList(), model, false)).jsonObject
    }

    @Test
    fun testTemperatureIsOmittedForModelsWithoutTemperatureCapability() {
        listOf(AnthropicModels.Opus_5, AnthropicModels.Sonnet_5).forEach { model ->
            assertFalse(
                model.supports(LLMCapability.Temperature),
                "Precondition: ${model.id} must not declare LLMCapability.Temperature"
            )
            assertNull(
                requestBodyFor(model)["temperature"],
                "temperature must not be serialized for ${model.id}"
            )
        }
    }

    @Test
    fun testTemperatureIsSentForModelsWithTemperatureCapability() {
        val model = AnthropicModels.Sonnet_4_6
        assertTrue(
            model.supports(LLMCapability.Temperature),
            "Precondition: ${model.id} must declare LLMCapability.Temperature"
        )

        assertEquals(
            0.7,
            requestBodyFor(model)["temperature"]?.jsonPrimitive?.content?.toDouble()
        )
    }
}
