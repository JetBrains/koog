package ai.koog.prompt.executor.ollama.client

import ai.koog.prompt.llm.LLModel

/**
 * Utility functions for working with Ollama models - fully dynamic approach.
 */
public object OllamaModelUtils {

    /**
     * Normalizes a model name by removing common variations.
     */
    public fun normalizeModelName(modelName: String): String {
        return modelName.lowercase()
            .trim()
            .replace(Regex("""[:\-_.]latest$"""), "") // Remove :latest suffix
    }

    /**
     * Extracts the base model name without version or size information.
     */
    public fun extractBaseModelName(modelName: String): String {
        return modelName.lowercase()
            .split(":")[0] // Take everything before the colon
            .replace(Regex("""[.\-_](\d+\.?\d*[bmkgt]?)$"""), "") // Remove size suffixes like .7b, -1b
    }

    /**
     * Extracts model size information from the model name.
     */
    public fun extractModelSize(modelName: String): String? {
        val sizeRegex = Regex(""":?(\d+(?:\.\d+)?[bmkgt]?)$""", RegexOption.IGNORE_CASE)
        return sizeRegex.find(modelName)?.groupValues?.get(1)
    }

    /**
     * Extracts model family from name using simple heuristics.
     */
    public fun extractModelFamily(modelName: String): String {
        val lowerName = modelName.lowercase()
        
        return when {
            lowerName.contains("llama") -> "llama"
            lowerName.contains("qwen") -> "qwen"
            lowerName.contains("gemma") -> "gemma"
            lowerName.contains("mistral") -> "mistral"
            lowerName.contains("phi") -> "phi"
            lowerName.contains("deepseek") -> "deepseek"
            lowerName.contains("codellama") -> "codellama"
            lowerName.contains("embed") || lowerName.contains("nomic") -> "embedding"
            lowerName.contains("all-minilm") -> "embedding"
            lowerName.contains("bge") -> "embedding"
            else -> "unknown"
        }
    }

    /**
     * Checks if a model name appears to be an embedding model.
     */
    public fun isEmbeddingModel(modelName: String): Boolean {
        val lowerName = modelName.lowercase()
        return lowerName.contains("embed") || 
               lowerName.contains("nomic") ||
               lowerName.contains("all-minilm") ||
               lowerName.contains("bge")
    }

    /**
     * Compares two model names to determine if they represent the same base model.
     */
    public fun isSameBaseModel(modelName1: String, modelName2: String): Boolean {
        return extractBaseModelName(modelName1) == extractBaseModelName(modelName2)
    }

    /**
     * Gets a user-friendly display name for a model.
     */
    public fun getDisplayName(modelName: String): String {
        val parts = modelName.split(":")
        val baseName = parts[0]
        val variant = parts.getOrNull(1)
        
        return if (variant != null && variant != "latest") {
            "$baseName ($variant)"
        } else {
            baseName
        }
    }

    /**
     * Validates a model name format.
     */
    public fun isValidModelName(modelName: String): Boolean {
        if (modelName.isBlank()) return false
        
        // Basic validation - model names should not contain certain characters
        val invalidChars = listOf(" ", "\t", "\n", "\\", "/", "\"", "'")
        if (invalidChars.any { modelName.contains(it) }) return false
        
        // Should follow the pattern: name or name:tag
        val regex = Regex("""^[a-zA-Z0-9][a-zA-Z0-9.\-_]*(?::[a-zA-Z0-9.\-_]+)?$""")
        return regex.matches(modelName)
    }

    /**
     * Suggests the best model variant from a list of available models based on system capabilities.
     * This is now fully dynamic - no hardcoded patterns!
     */
    public fun suggestBestVariant(
        availableModels: List<String>, 
        baseModelName: String, 
        availableMemoryGB: Int = 8
    ): String? {
        // Find all models that match the base name
        val matchingModels = availableModels.filter { 
            isSameBaseModel(it, baseModelName) 
        }
        
        if (matchingModels.isEmpty()) return null
        
        // Sort by preference based on memory
        val sortedModels = matchingModels.sortedWith { model1, model2 ->
            val size1 = extractModelSizeInGB(model1)
            val size2 = extractModelSizeInGB(model2)
            
            // Prefer models that fit in memory, then by size
            when {
                size1 <= availableMemoryGB && size2 > availableMemoryGB -> -1
                size1 > availableMemoryGB && size2 <= availableMemoryGB -> 1
                else -> size1.compareTo(size2)
            }
        }
        
        return sortedModels.firstOrNull()
    }

    /**
     * Estimates model size in GB from model name.
     */
    private fun extractModelSizeInGB(modelName: String): Double {
        val sizeStr = extractModelSize(modelName) ?: return 8.0 // Default assumption
        
        return when {
            sizeStr.endsWith("b", ignoreCase = true) -> {
                val number = sizeStr.dropLast(1).toDoubleOrNull() ?: 8.0
                number * 2 // Rough estimation: 1B params ≈ 2GB
            }
            sizeStr.endsWith("m", ignoreCase = true) -> {
                val number = sizeStr.dropLast(1).toDoubleOrNull() ?: 4000.0
                number / 500 // Rough estimation: 500M params ≈ 1GB
            }
            else -> 8.0 // Default
        }
    }

    /**
     * Groups models by their base name from a list of available models.
     */
    public fun groupModelsByBase(availableModels: List<String>): Map<String, List<String>> {
        return availableModels.groupBy { extractBaseModelName(it) }
    }

    /**
     * Groups models by their family from a list of available models.
     */
    public fun groupModelsByFamily(availableModels: List<String>): Map<String, List<String>> {
        return availableModels.groupBy { extractModelFamily(it) }
    }

    /**
     * Finds similar models by comparing base names and families.
     */
    public fun findSimilarModels(
        availableModels: List<String>, 
        targetModel: String, 
        limit: Int = 5
    ): List<String> {
        val targetBase = extractBaseModelName(targetModel)
        val targetFamily = extractModelFamily(targetModel)
        val lowerTarget = targetModel.lowercase()
        
        return availableModels
            .filter { it.lowercase() != lowerTarget } // Exclude exact match
            .sortedBy { model ->
                val score = when {
                    // Exact base match gets highest priority
                    extractBaseModelName(model) == targetBase -> 0
                    // Same family gets second priority
                    extractModelFamily(model) == targetFamily -> 1
                    // Contains target name gets third priority
                    model.lowercase().contains(lowerTarget) -> 2
                    // Everything else
                    else -> 3
                }
                score
            }
            .take(limit)
    }

    /**
     * Gets general use case categories - these are the only "static" mappings we keep,
     * but they're based on model family, not specific model names.
     */
    public fun getModelUseCaseByFamily(family: String): String {
        return when (family) {
            "llama" -> "General purpose chat and reasoning"
            "qwen" -> "Multilingual and coding tasks"
            "gemma" -> "Efficient general purpose"
            "mistral" -> "Conversation and text generation"
            "phi" -> "Efficient small models"
            "deepseek" -> "Code understanding and reasoning"
            "codellama" -> "Code generation and analysis"
            "embedding" -> "Text embeddings for RAG and similarity"
            else -> "General purpose language model"
        }
    }
}

/**
 * Extension functions for easier model creation - now fully dynamic.
 */

/**
 * Creates an LLModel from a simple model name string.
 */
public suspend fun OllamaClient.modelFromName(modelName: String): LLModel {
    return this.modelFactory().createFromModelName(modelName)
}

/**
 * Creates an LLModel and pulls it if necessary.
 */
public suspend fun OllamaClient.modelFromNameOrPull(modelName: String): LLModel {
    return this.modelFactory().createFromModelName(modelName, pullIfMissing = true)
}

/**
 * Lists all available models grouped by family - fully dynamic.
 */
public suspend fun OllamaClient.listModelsGrouped(): Map<String, List<String>> {
    return this.modelFactory().listModelsByFamily()
}

/**
 * Gets the best available model for a base name and memory constraint.
 */
public suspend fun OllamaClient.getBestModelVariant(
    baseModelName: String, 
    availableMemoryGB: Int = 8
): LLModel? {
    val availableModels = this.getAvailableModels().map { it.name }
    val bestVariant = OllamaModelUtils.suggestBestVariant(
        availableModels, 
        baseModelName, 
        availableMemoryGB
    )
    
    return bestVariant?.let { this.modelFromName(it) }
}

/**
 * Finds similar models to a target model.
 */
public suspend fun OllamaClient.findSimilarModels(
    targetModel: String, 
    limit: Int = 5
): List<LLModel> {
    val availableModels = this.getAvailableModels().map { it.name }
    val similarNames = OllamaModelUtils.findSimilarModels(availableModels, targetModel, limit)
    
    return similarNames.mapNotNull { 
        try { 
            this.modelFromName(it) 
        } catch (e: Exception) { 
            null 
        } 
    }
}
