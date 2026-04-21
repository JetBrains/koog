package ai.koog.prompt.executor.ollama.client

import ai.koog.prompt.executor.clients.list
import ai.koog.prompt.llm.LLMProvider
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class OpenAIModelsTest {

    @Test
    fun `OpenAI models should have OpenAI provider`() {
        val models = OllamaModels.list()

        models.forEach { model ->
            model.provider shouldBe LLMProvider.Ollama
        }
    }

    @Test
    fun `OpenAIModels models should return all declared models`() {
        val reflectionModels = OllamaModels.list().map { it.id }.toSet()

        val models = OllamaModels.models.map { it.id }.toSet()

        assert(models.size == reflectionModels.size)

        reflectionModels.forEach { model ->
            models shouldContain model
        }
    }
}
