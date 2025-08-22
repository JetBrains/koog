package deepseek

import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.executor.clients.list
import ai.koog.prompt.llm.LLMProvider
import kotlin.test.Test
import kotlin.test.assertSame

class DeepSeekModelsTest {

    @Test
    fun `DeepSeek models should have DeepSeek provider`() {
        val models = DeepSeekModels.list()

        models.forEach { model ->
            assertSame(
                expected = LLMProvider.DeepSeek,
                actual = model.provider,
                message = "DeepSeek model ${model.id} doesn't have DeepSeek provider but ${model.provider}."
            )
        }
    }
}
