package ai.koog.prompt.executor.ollama.client

import ai.koog.prompt.llm.LLModel
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Factory for creating and managing Ollama models - fully dynamic approach.
 */
public class OllamaModelFactory(
    private val client: OllamaClient
) {
    private val logger = KotlinLogging.logger { }

    /**
     * Creates a model from any Ollama model name with automatic capability detection.
     * This is the primary method for creating models from Ollama model names.
     *
     * @param modelName The Ollama model name (e.g., "llama3.2:1b", "qwen2.5-coder:32b")
     * @param validateAvailability Whether to check if the model is available locally
     * @param pullIfMissing Whether to pull the model if it's not available locally
     * @return An LLModel instance with detected capabilities
     * @throws IllegalArgumentException if the model is not available and pullIfMissing is false
     */
    public suspend fun createFromModelName(
        modelName: String,
        validateAvailability: Boolean = true,
        pullIfMissing: Boolean = false
    ): LLModel {
        logger.info { "Creating model for: $modelName" }


        client.resolveModel(modelName)?.let {
            logger.info { "Resolved existing model: ${it.id}" }
            return it
        }

        if (validateAvailability) {
            val isAvailable = client.validateModel(modelName)

            if (!isAvailable) {
                if (pullIfMissing) {
                    logger.info { "Model $modelName not found locally, attempting to pull..." }
                    val pullSuccess = client.pullModel(modelName)
                    if (!pullSuccess) {
                        val suggestions = client.suggestSimilarModels(modelName)
                        val suggestionText = if (suggestions.isNotEmpty()) {
                            " Similar available models: ${suggestions.joinToString(", ")}"
                        } else ""
                        throw IllegalArgumentException("Failed to pull model '$modelName'.$suggestionText")
                    }
                    logger.info { "Successfully pulled model: $modelName" }
                } else {
                    val suggestions = client.suggestSimilarModels(modelName)
                    val suggestionText = if (suggestions.isNotEmpty()) {
                        " Similar available models: ${suggestions.joinToString(", ")}"
                    } else ""
                    throw IllegalArgumentException("Model '$modelName' is not available locally.$suggestionText")
                }
            }
        }

        // Create dynamic model
        val model = client.createDynamicModel(modelName)
        logger.info { "Created dynamic model: ${model.id} with capabilities: ${model.capabilities}" }
        return model
    }

    /**
     * Lists all locally available models.
     */
    public suspend fun listAvailableModels(): List<String> {
        return client.getAvailableModels().map { it.name }
    }

    /**
     * Groups models by family from available models - fully dynamic.
     */
    public suspend fun listModelsByFamily(): Map<String, List<String>> {
        val models = client.getAvailableModels().map { it.name }
        return OllamaModelUtils.groupModelsByFamily(models)
    }

    /**
     * Gets model information including size and modification date - fully dynamic.
     */
    public suspend fun getModelInfo(modelName: String): ModelInfo? {
        val models = client.getAvailableModels()
        val model = models.find {
            it.name.equals(modelName, ignoreCase = true) ||
                    it.model.equals(modelName, ignoreCase = true) ||
                    OllamaModelUtils.isSameBaseModel(it.name, modelName)
        } ?: return null

        val family = OllamaModelUtils.extractModelFamily(model.name)
        val capabilities = detectCapabilitiesFromFamily(family)

        return ModelInfo(
            name = model.name,
            size = formatSize(model.size),
            modifiedAt = model.modifiedAt,
            family = family,
            capabilities = capabilities
        )
    }

    /**
     * Creates multiple models from a list of model names.
     */
    public suspend fun createModelsFromNames(
        modelNames: List<String>,
        validateAvailability: Boolean = true,
        pullIfMissing: Boolean = false
    ): Map<String, LLModel> {
        val results = mutableMapOf<String, LLModel>()

        for (modelName in modelNames) {
            try {
                val model = createFromModelName(modelName, validateAvailability, pullIfMissing)
                results[modelName] = model
            } catch (e: Exception) {
                logger.error(e) { "Failed to create model for: $modelName" }
                // Continue with other models
            }
        }

        return results
    }

    /**
     * Finds models by partial name match - fully dynamic.
     */
    public suspend fun findModelsByPattern(pattern: String): List<String> {
        val availableModels = client.getAvailableModels().map { it.name }
        val regex = pattern.replace("*", ".*").toRegex(RegexOption.IGNORE_CASE)

        return availableModels.filter {
            regex.matches(it)
        }
    }

    /**
     * Creates a model with specific capabilities override.
     */
    public suspend fun createWithCapabilities(
        modelName: String,
        capabilities: List<ai.koog.prompt.llm.LLMCapability>
    ): LLModel {
        val baseModel = createFromModelName(modelName, validateAvailability = false)

        return LLModel(
            provider = baseModel.provider,
            id = baseModel.id,
            capabilities = capabilities
        )
    }

    /**
     * Detects capabilities from family name - using utility approach.
     */
    private fun detectCapabilitiesFromFamily(family: String): List<String> {
        return when (family) {
            "embedding" -> listOf("Embedding")
            "llama", "qwen", "gemma", "mistral", "deepseek" -> listOf(
                "Chat",
                "Temperature",
                "Streaming",
                "Tools",
                "JSON Schema"
            )

            "phi", "codellama" -> listOf("Chat", "Temperature", "Streaming")
            else -> listOf("Chat", "Temperature", "Streaming")
        }
    }

    private fun formatSize(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = bytes.toDouble()
        var unitIndex = 0

        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }

        return "${(size * 10).toInt() / 10.0} ${units[unitIndex]}"
    }
}

/**
 * Model information data class.
 */
public data class ModelInfo(
    val name: String,
    val size: String,
    val modifiedAt: String,
    val family: String,
    val capabilities: List<String>
)

/**
 * Extension function to create an OllamaModelFactory from an OllamaClient.
 */
public fun OllamaClient.modelFactory(): OllamaModelFactory = OllamaModelFactory(this)
