package ai.koog.prompt.executor.clients.openai

import ai.koog.prompt.executor.clients.list
import ai.koog.prompt.llm.LLMProvider
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class OpenAIModelsTest {

    @Test
    fun `OpenAI models should have OpenAI provider`() {
        val models = OpenAIModels.list()

        models.forEach { model ->
            model.provider shouldBe LLMProvider.OpenAI
        }
    }
}
