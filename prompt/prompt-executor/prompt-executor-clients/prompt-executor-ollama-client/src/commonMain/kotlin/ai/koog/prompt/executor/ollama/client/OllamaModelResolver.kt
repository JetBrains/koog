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
    private val logger = KotlinLogging.logger { }

    /**
     * Only the absolute minimum static mappings for backward compatibility.
     * These are for the existing hardcoded models in the codebase.
     */
    private val backwardCompatibilityMappings = mapOf(
        "meta-llama-3-2" to "llama3.2",
        "alibaba-qwq" to "qwq",
        "alibaba-qwen-coder-2-5-32b" to "qwen2.5-coder:32b"
    )

    /**
     * Resolves an LLModel to the actual Ollama model name - fully dynamic.
     */
    public suspend fun resolveToOllamaName(model: LLModel): String {
        // Check backward compatibility mappings first (only for existing hardcoded models)
        backwardCompatibilityMappings[model.id]?.let { 
            logger.debug { "Resolved ${model.id} to $it via backward compatibility mapping" }
            return it 
        }

        // If the model ID starts with "ollama-", extract the actual model name
        if (model.id.startsWith("ollama-")) {
            val extractedName = model.id.removePrefix("ollama-")
            if (extractedName.startsWith("dynamic-")) {
                return extractedName.removePrefix("dynamic-")
            }
            return extractedName
        }

        // Try to find the model in available models by exact match
        val availableModels = modelManager.getAvailableModels()
        
        // First try exact match
        availableModels.find { it.name.equals(model.id, ignoreCase = true) }?.let {
            logger.debug { "Found exact match for ${model.id} as ${it.name}" }
            return it.name
        }

        // Try to find by partial match
        availableModels.find { 
            it.name.contains(model.id, ignoreCase = true) ||
            it.model.contains(model.id, ignoreCase = true)
        }?.let {
            logger.debug { "Found partial match for ${model.id} as ${it.name}" }
            return it.name
        }

        // As a last resort, use the model ID directly
        logger.warn { "No mapping found for model ${model.id}, using ID directly" }
        return model.id
    }

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
