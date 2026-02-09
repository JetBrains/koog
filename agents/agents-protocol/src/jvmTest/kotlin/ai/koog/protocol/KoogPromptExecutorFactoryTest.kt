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
    fun testResolveModelNullDefaultsToGpt4o() {
        val model = KoogPromptExecutorFactory.resolveModel(null, defaultModel = null)
        assertEquals(OpenAIModels.Chat.GPT4o, model, "Null modelString should default to OpenAI GPT-4o")
    }

    @Test
    fun testResolveModelOpenAIShortFormResolves() {
        // Using short form "openai/gpt4o" (category will be auto-resolved to chat)
        val model = KoogPromptExecutorFactory.resolveModel("openai/gpt4o", defaultModel = null)
        assertEquals(OpenAIModels.Chat.GPT4o, model)
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
