package ai.koog.prompt.executor.clients.openrouter

import ai.koog.prompt.executor.clients.list
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
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

    @Test
    fun `OpenRouter models note Document capability appropriately`() {
        val expectedDocumentCapableModels = listOf(
            OpenRouterModels.Claude3_5Sonnet,
            OpenRouterModels.Claude3_7Sonnet,
            OpenRouterModels.Claude4Sonnet,
            OpenRouterModels.Claude4_1Opus,
            OpenRouterModels.Claude4_5Sonnet,
            OpenRouterModels.Claude4_5Opus,
            OpenRouterModels.Gemini2_5Flash,
            OpenRouterModels.Gemini2_5FlashLite,
            OpenRouterModels.Gemini2_5Pro,
            OpenRouterModels.GPT4oMini,
            OpenRouterModels.GPT4o,
            OpenRouterModels.GPT5,
            OpenRouterModels.GPT5Chat,
            OpenRouterModels.GPT5Mini,
            OpenRouterModels.GPT5Nano,
            OpenRouterModels.GPT5_2,
            OpenRouterModels.GPT5_2Pro,
        )
        
        expectedDocumentCapableModels.forEach { model ->
            assertContains(
                iterable = model.capabilities,
                element = LLMCapability.Document,
                message = "OpenRouter model ${model.id} doesn't have Document capability."
            )
        }

        val models = OpenRouterModels.list()

        models.forEach { model ->
            if (model in expectedDocumentCapableModels) return@forEach

            assertFalse(
                actual = model.capabilities.contains(LLMCapability.Document),
                message = "OpenRouter model ${model.id} has Document capability."
            )
        }
    }
}
