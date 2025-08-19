package ai.koog.prompt.executor.clients.mistralai

import ai.koog.prompt.executor.clients.list
import ai.koog.prompt.executor.clients.mistralai.model.MistralAIChatCompletionRequest
import ai.koog.prompt.llm.LLMProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class MistralAIModelsTest {

    @Test
    fun `MistralAI models should have MistralAI provider`() {
        val models = MistralAIModels.list()

        models.forEach { model ->
            assertSame(
                expected = LLMProvider.MistralAI,
                actual = model.provider,
                message = "Mistral AI model ${model.id} doesn't have MistralAI provider but ${model.provider}."
            )
        }
    }

    @Test
    fun `MistralAIChatCompletionRequest should reject negative maxTokens`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            MistralAIChatCompletionRequest(
                model = MistralAIModels.MISTRAL_MEDIUM_3_1.id,
                messages = emptyList(),
                maxTokens = -1
            )
        }
        assertEquals("maxTokens must be greater or equal to 0, but was -1", exception.message)
    }
}
