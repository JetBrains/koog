package dashscope

import ai.koog.prompt.executor.clients.dashscope.DashscopeModels
import ai.koog.prompt.executor.clients.list
import ai.koog.prompt.llm.LLMProvider
import io.kotest.matchers.collections.shouldContain
import kotlin.test.Test
import kotlin.test.assertSame

class DashscopeModelsTest {

    @Test
    fun `DashScope models should have DashScope provider`() {
        val models = DashscopeModels.list()

        models.forEach { model ->
            assertSame(
                expected = LLMProvider.Alibaba,
                actual = model.provider,
                message = "DashScope model ${model.id} doesn't have DashScope provider but ${model.provider}."
            )
        }
    }

    @Test
    fun `DashscopeModels models should return all declared models`() {
        val reflectionModels = DashscopeModels.list().map { it.id }

        val models = DashscopeModels.models.map { it.id }

        assert(models.size == reflectionModels.size)

        reflectionModels.forEach { model ->
            models shouldContain model
        }
    }
}
