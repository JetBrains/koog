package ai.koog.prompt.executor.clients.mistralai

import ai.koog.prompt.executor.clients.list
import ai.koog.prompt.llm.LLMProvider
import io.kotest.matchers.collections.shouldContain
import kotlin.test.Test
import kotlin.test.assertSame

class MistralAIModelsTest {

    @Test
    fun `MistralAIModels should have MistralAI provider`() {
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
    fun `MistralAIModels should return all declared models`() {
        val reflectionModels = MistralAIModels.list().map { it.id }

        val models = MistralAIModels.models.map { it.id }

        assert(models.size == reflectionModels.size)

        reflectionModels.forEach { model ->
            models shouldContain model
        }
    }
}
