package ai.koog.prompt.executor.clients.openrouter

import ai.koog.prompt.executor.clients.list
import ai.koog.prompt.llm.LLMProvider
import kotlin.test.Test
import kotlin.test.assertSame

class OpenRouterModelsTest {

    @Test
    fun `OpenRouter models should have OpenRouter provider`() {
        val models = OpenRouterModels.list()

        models.forEach { model ->
            assertSame(
                expected = LLMProvider.OpenRouter,
                actual = model.provider,
                message = "OpenRouter model ${model.id} doesn't have OpenRouter provider but ${model.provider}."
            )
        }
    }
}
