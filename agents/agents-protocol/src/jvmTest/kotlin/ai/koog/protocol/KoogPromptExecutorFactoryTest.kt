package ai.koog.protocol

import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.protocol.flow.KoogPromptExecutorFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KoogPromptExecutorFactoryTest {

    //region resolveModel tests

    @Test
    fun testResolveModelOpenAIShortFormResolves() {
        // Using short form "openai/gpt4o" (category will be auto-resolved to chat)
        val model = KoogPromptExecutorFactory.resolveModel("openai/gpt4o")
        assertEquals(OpenAIModels.Chat.GPT4o, model)
    }

    @Test
    fun testResolveModelOllamaWithMakerResolves() {
        val model = KoogPromptExecutorFactory.resolveModel("ollama/meta/llama3.2:3b")
        assertEquals(OllamaModels.Meta.LLAMA_3_2_3B, model)
    }

    @Test
    fun testResolveModelInvalidFormatThrowsError() {
        val exception = assertFailsWith<IllegalStateException> {
            KoogPromptExecutorFactory.resolveModel("invalid-format")
        }

        val message = exception.message
        assertNotNull(message)
        assertTrue(
            message.contains("Invalid model string format"),
            "Exception should mention invalid format, got: ${exception.message}"
        )
    }

    @Test
    fun testResolveModelUnknownModelThrowsError() {
        val exception = assertFailsWith<IllegalStateException> {
            KoogPromptExecutorFactory.resolveModel("openai/nonexistent-model")
        }

        val message = exception.message
        assertNotNull(message)
        assertTrue(
            message.contains("Unable to find model identifier"),
            "Exception should mention unable to find model, got: ${exception.message}"
        )
    }

    //endregion resolveModel tests

    //region resolveModelOrNull tests

    @Test
    fun testResolveModelOrNullValidModelReturnsModel() {
        val model = KoogPromptExecutorFactory.resolveModelOrNull("openai/gpt4o")
        assertNotNull(model)
        assertEquals(OpenAIModels.Chat.GPT4o, model)
    }

    @Test
    fun testResolveModelOrNullInvalidFormatReturnsNull() {
        val model = KoogPromptExecutorFactory.resolveModelOrNull("invalid-format")
        assertNull(model, "Should return null for invalid model string format")
    }

    @Test
    fun testResolveModelOrNullUnknownModelReturnsNull() {
        val model = KoogPromptExecutorFactory.resolveModelOrNull("openai/nonexistent-model")
        assertNull(model, "Should return null for unknown model identifier")
    }

    //endregion resolveModelOrNull tests

    //region buildFromModels tests

    @Test
    fun testBuildFromModelsWithEmptyListReturnsNull() {
        val executor = KoogPromptExecutorFactory.buildFromModels(emptyList())
        assertNull(executor, "Executor should be null when no models are provided")
    }

    @Test
    fun testBuildFromModelsWithOnlyOllamaReturnsExecutor() {
        // Ollama client does not require an API key, so the executor must be created
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

    //endregion buildFromModels tests
}
