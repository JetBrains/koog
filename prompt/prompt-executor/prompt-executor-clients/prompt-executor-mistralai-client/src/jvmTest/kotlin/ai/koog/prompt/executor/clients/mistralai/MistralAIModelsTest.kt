package ai.koog.prompt.executor.clients.mistralai

import ai.koog.prompt.executor.clients.list
import ai.koog.prompt.llm.LLMProvider
import kotlin.test.Test
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
}
