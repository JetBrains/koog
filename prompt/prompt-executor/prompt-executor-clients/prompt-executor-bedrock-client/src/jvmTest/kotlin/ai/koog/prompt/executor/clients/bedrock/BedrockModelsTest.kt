package ai.koog.prompt.executor.clients.bedrock

import ai.koog.prompt.executor.clients.list
import ai.koog.prompt.llm.LLMProvider
import io.kotest.matchers.collections.shouldContain
import kotlin.test.Test
import kotlin.test.assertSame

class BedrockModelsTest {

    @Test
    fun `BedrockModels models should have Bedrock provider`() {
        val models = BedrockModels.list()

        models.forEach { model ->
            assertSame(
                expected = LLMProvider.Bedrock,
                actual = model.provider,
                message = "Bedrock model ${model.id} doesn't have Bedrock provider but ${model.provider}."
            )
        }
    }

    @Test
    fun `BedrockModels models should return all declared models`() {
        val reflectionModels = BedrockModels.list().map { it.id }

        val models = BedrockModels.models.map { it.id }

        assert(models.size == reflectionModels.size)

        reflectionModels.forEach { model ->
            models shouldContain model
        }
    }
}
