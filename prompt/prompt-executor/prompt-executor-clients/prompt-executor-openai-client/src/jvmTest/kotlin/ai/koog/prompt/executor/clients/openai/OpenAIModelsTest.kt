package ai.koog.prompt.executor.clients.openai

import ai.koog.prompt.executor.clients.list
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.test.assertNotNull

class OpenAIModelsTest {

    @Test
    fun `OpenAI models should have OpenAI provider`() {
        val models = OpenAIModels.list()

        models.forEach { model ->
            model.provider shouldBe LLMProvider.OpenAI
        }
    }

    @Test
    fun `OpenAIModels models should return all declared models`() {
        val reflectionModels = OpenAIModels.list().map { it.id }

        val models = OpenAIModels.models.map { it.id }

        assert(models.size == reflectionModels.size)

        reflectionModels.forEach { model ->
            models shouldContain model
        }
    }

    @Test
    fun `reasoning OpenAI models should advertise thinking capability`() {
        assertNotNull(OpenAIModels.Chat.O1.capabilities) shouldContain LLMCapability.Thinking
        assertNotNull(OpenAIModels.Chat.O3.capabilities) shouldContain LLMCapability.Thinking
        assertNotNull(OpenAIModels.Chat.O3Mini.capabilities) shouldContain LLMCapability.Thinking
        assertNotNull(OpenAIModels.Chat.O4Mini.capabilities) shouldContain LLMCapability.Thinking
        assertNotNull(OpenAIModels.Chat.GPT5.capabilities) shouldContain LLMCapability.Thinking
        assertNotNull(OpenAIModels.Chat.GPT5_4.capabilities) shouldContain LLMCapability.Thinking
        assertNotNull(OpenAIModels.Chat.GPT5_4Mini.capabilities) shouldContain LLMCapability.Thinking
        assertNotNull(OpenAIModels.Chat.GPT5_4Nano.capabilities) shouldContain LLMCapability.Thinking
    }
}
