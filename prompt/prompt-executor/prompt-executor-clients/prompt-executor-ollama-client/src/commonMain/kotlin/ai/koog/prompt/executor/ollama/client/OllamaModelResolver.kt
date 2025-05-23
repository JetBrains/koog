package ai.koog.prompt.executor.ollama.client

import ai.koog.prompt.llm.LLModel
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Resolves LLModel instances to actual Ollama model names - fully dynamic approach.
 * No hardcoded patterns - everything is discovered from Ollama's API.
 */
public class OllamaModelResolver(
    private val modelManager: OllamaModelManager
) {
    /**
     * Attempts to resolve a model name and create an LLModel if it doesn't exist.
     */
    public suspend fun resolveOrCreate(modelName: String): Pair<LLModel, String> {
        // Create a dynamic model - no need to check for "existing" models
        // since we're discovering everything dynamically
        val dynamicModel = modelManager.createDynamicModel(modelName)
        return dynamicModel to modelName
    }

    /**
     * Validates that a model name is available in Ollama - purely dynamic.
     */
    public suspend fun validateModelAvailable(modelName: String): Boolean {
        val availableModels = modelManager.getAvailableModels()
        return availableModels.any { 
            it.name.equals(modelName, ignoreCase = true) ||
            it.model.equals(modelName, ignoreCase = true) ||
            // Also check for partial matches (e.g., "llama3.2" matches "llama3.2:3b")
            it.name.startsWith(modelName, ignoreCase = true) ||
            OllamaModelUtils.isSameBaseModel(it.name, modelName)
        }
    }

    /**
     * Suggests available models that are similar to the requested name - fully dynamic.
     */
    public suspend fun suggestSimilarModels(modelName: String, limit: Int = 5): List<String> {
        val availableModels = modelManager.getAvailableModels().map { it.name }
        return OllamaModelUtils.findSimilarModels(availableModels, modelName, limit)
    }

    /**
     * Gets all variants of a model that are actually available in Ollama.
     */
    public suspend fun getAvailableVariants(baseModelName: String): List<String> {
        val availableModels = modelManager.getAvailableModels().map { it.name }
        return availableModels.filter { 
            OllamaModelUtils.isSameBaseModel(it, baseModelName) 
        }
    }

    /**
     * Suggests the best available variant for a model based on system constraints.
     */
    public suspend fun suggestBestAvailableVariant(
        baseModelName: String, 
        availableMemoryGB: Int = 8
    ): String? {
        val availableModels = modelManager.getAvailableModels().map { it.name }
        return OllamaModelUtils.suggestBestVariant(availableModels, baseModelName, availableMemoryGB)
    }
}
