package ai.koog.protocol

import ai.koog.protocol.flow.KoogPromptExecutorFactory
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.llm.OllamaModels
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class KoogPromptExecutorFactoryTest {
    @Test
    fun testResolveModelBothNullReturnsNull() {
        val model = KoogPromptExecutorFactory.resolveModel("")
        assertNull(model, "Should return null when both modelString and defaultModel are null")
    }

    @Test
    fun testResolveModelUsesDefaultModel() {
        val model = KoogPromptExecutorFactory.resolveModel(null, defaultModel = "ollama/meta/llama3.2:3b")
        assertEquals(OllamaModels.Meta.LLAMA_3_2_3B, model, "Should use defaultModel when modelString is null")
    }

    @Test
    fun testResolveModelPrefersModelStringOverDefault() {
        val model = KoogPromptExecutorFactory.resolveModel("openai/gpt4o", defaultModel = "ollama/meta/llama3.2:3b")
        assertEquals(OpenAIModels.Chat.GPT4o, model, "Should prefer modelString over defaultModel")
    }

    @Test
    fun testResolveModelOpenAIShortFormResolves() {
        // Using short form "openai/gpt4o" (category will be auto-resolved to chat)
        val model = KoogPromptExecutorFactory.resolveModel("openai/gpt4o", defaultModel = null)
        assertEquals(OpenAIModels.Chat.GPT4o, model)
    }

    @Test
    fun testResolveModelInvalidFormatReturnsNull() {
        val model = KoogPromptExecutorFactory.resolveModel("invalid-format", defaultModel = null)
        assertNull(model, "Should return null for invalid model string format")
    }

    @Test
    fun testResolveModelEmptyProviderReturnsNull() {
        val model = KoogPromptExecutorFactory.resolveModel("/model", defaultModel = null)
        assertNull(model, "Should return null when provider is empty")
    }

    @Test
    fun testResolveModelEmptyModelIdReturnsNull() {
        val model = KoogPromptExecutorFactory.resolveModel("provider/", defaultModel = null)
        assertNull(model, "Should return null when model-id is empty")
    }

    @Test
    fun testResolveModelUnknownModelReturnsNull() {
        val model = KoogPromptExecutorFactory.resolveModel("openai/nonexistent-model", defaultModel = null)
        assertNull(model, "Should return null for unknown model identifier")
    }

    @Test
    fun testBuildFromModelsWithEmptyListReturnsNull() {
        val executor = KoogPromptExecutorFactory.buildFromModels(emptyList())
        assertNull(executor, "Executor should be null when no models are provided")
    }

    @Test
    fun testBuildFromModelsWithOnlyOllamaReturnsExecutor() {
        // Ollama client does not require an API key, so executor must be created
        val executor = KoogPromptExecutorFactory.buildFromModels(listOf(OllamaModels.Meta.LLAMA_4))
        assertNotNull(executor, "Executor should be created for Ollama models without API keys")
    }

    @Test
    fun testBuildFromModelsWithMixedProvidersCreatesExecutorIfAtLeastOneClientAvailable() {
        // Even if other providers miss API keys, Ollama should still allow creating the executor
        val models = listOf(
            OllamaModels.Meta.LLAMA_3_2_3B,
            OpenAIModels.Chat.GPT4o
        )

        val executor = KoogPromptExecutorFactory.buildFromModels(models)
        assertNotNull(executor, "Executor should be created when at least one provider client can be initialized")
    }
}
