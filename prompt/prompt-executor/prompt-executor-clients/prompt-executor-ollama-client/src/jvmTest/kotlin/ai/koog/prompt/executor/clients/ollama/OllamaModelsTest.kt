package ai.koog.prompt.executor.clients.ollama

import ai.koog.prompt.executor.clients.list
import ai.koog.prompt.llm.LLMProvider
import kotlin.test.Test
import kotlin.test.assertSame

class OllamaModelsTest {

    @Test
    fun `Ollama models should have Ollama provider`() {
        val models = OllamaModels.list() + OllamaEmbeddingModels.list()

        models.forEach { model ->
            assertSame(
                expected = LLMProvider.Ollama,
                actual = model.provider,
                message = "Ollama model ${model.id} doesn't have Ollama provider but ${model.provider}."
            )
        }
    }
}
