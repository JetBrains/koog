package ai.koog.prompt.executor.clients.anthropic

import ai.koog.prompt.Prompt
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class AnthropicModelResolutionTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = AnthropicLLMClient(apiKey = "test-key")

    private fun requestModelField(model: LLModel): String? {
        val requestJson = client.createAnthropicRequest(
            prompt = Prompt(messages = emptyList(), id = "id"),
            tools = emptyList(),
            model = model,
            stream = false
        )
        return json.parseToJsonElement(requestJson).jsonObject["model"]?.jsonPrimitive?.content
    }

    @Test
    fun testMappedModelResolvesThroughVersionsMap() {
        assertEquals(
            DEFAULT_ANTHROPIC_MODEL_VERSIONS_MAP.getValue(AnthropicModels.Sonnet_4),
            requestModelField(AnthropicModels.Sonnet_4)
        )
    }

    @Test
    fun testUnmappedModelFallsBackToId() {
        val custom = LLModel(
            provider = LLMProvider.Anthropic,
            id = "claude-sonnet-4-5",
            capabilities = listOf(LLMCapability.Completion, LLMCapability.Tools),
            contextLength = 200_000,
            maxOutputTokens = 64_000,
        )
        assertEquals("claude-sonnet-4-5", requestModelField(custom))
    }
}
