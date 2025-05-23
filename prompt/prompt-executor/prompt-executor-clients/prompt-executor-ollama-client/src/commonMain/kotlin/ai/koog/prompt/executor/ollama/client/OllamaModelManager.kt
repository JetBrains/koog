package ai.koog.prompt.executor.ollama.client

import ai.koog.prompt.executor.ollama.client.dto.*
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Manages Ollama models, providing dynamic discovery and capabilities detection.
 */
public class OllamaModelManager(
    private val client: HttpClient,
    private val baseUrl: String
) {
    private val logger = KotlinLogging.logger { }

    /**
     * Cache for discovered models to avoid repeated API calls.
     */
    private var modelsCache: List<OllamaModelInfo>? = null
    private var lastCacheUpdate: Long = 0
    private val cacheValidityMs = 300_000L // 5 minutes

    /**
     * Dynamic capability detection based on model family - minimal hardcoding.
     * We only define the general capability patterns, not specific models.
     */
    private fun getCapabilitiesForFamily(family: String): List<LLMCapability> {
        return when (family) {
            "embedding" -> listOf(LLMCapability.Embed)
            "llama", "qwen", "gemma", "mistral", "deepseek" -> listOf(
                LLMCapability.Temperature,
                LLMCapability.Schema.JSON.Simple,
                LLMCapability.Tools,
                LLMCapability.Completion
            )
            "phi", "codellama" -> listOf(
                LLMCapability.Temperature,
                LLMCapability.Completion
            )
            else -> listOf(
                LLMCapability.Temperature,
                LLMCapability.Completion
            )
        }
    }

    /**
     * Retrieves all available models from Ollama.
     */
    @OptIn(ExperimentalTime::class)
    public suspend fun getAvailableModels(): List<OllamaModelInfo> {
        val now = Clock.System.now().epochSeconds
        
        // Return cached models if still valid
        if (modelsCache != null && (now - lastCacheUpdate) < cacheValidityMs) {
            return modelsCache!!
        }

        return try {
            val response = client.get("$baseUrl/api/tags") {
                contentType(ContentType.Application.Json)
            }.body<OllamaModelsListResponse>()
            
            modelsCache = response.models
            lastCacheUpdate = now
            
            logger.info { "Discovered ${response.models.size} Ollama models" }
            response.models
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch available models from Ollama" }
            emptyList()
        }
    }

    /**
     * Resolves a model name to an LLModel instance with detected capabilities.
     */
    public suspend fun resolveModel(modelName: String): LLModel? {
        val availableModels = getAvailableModels()
        val matchedModel = findModelByName(availableModels, modelName)
            ?: return null

        val capabilities = detectCapabilities(matchedModel)
        
        return LLModel(
            provider = LLMProvider.Ollama,
            id = matchedModel.name,
            capabilities = capabilities,
        )
    }

    /**
     * Finds a model by name, supporting various naming conventions - purely dynamic.
     */
    private fun findModelByName(models: List<OllamaModelInfo>, targetName: String): OllamaModelInfo? {
        // Direct name match
        models.find { it.name.equals(targetName, ignoreCase = true) }?.let { return it }
        
        // Model field match (sometimes different from name)  
        models.find { it.model.equals(targetName, ignoreCase = true) }?.let { return it }
        
        // Partial match (e.g., "llama3.2" matches "llama3.2:1b")
        models.find { 
            it.name.startsWith(targetName, ignoreCase = true) ||
            it.model.startsWith(targetName, ignoreCase = true)
        }?.let { return it }
        
        // Base model name match using utility function
        models.find { 
            OllamaModelUtils.isSameBaseModel(it.name, targetName) ||
            OllamaModelUtils.isSameBaseModel(it.model, targetName)
        }?.let { return it }
        
        return null
    }

    /**
     * Detects capabilities based on model information - purely dynamic.
     */
    private suspend fun detectCapabilities(modelInfo: OllamaModelInfo): List<LLMCapability> {
        // Try to get detailed model information
        val details = getModelDetails(modelInfo.name)
        val family = details?.details?.family?.lowercase() 
            ?: OllamaModelUtils.extractModelFamily(modelInfo.name)

        // Use dynamic capability detection
        return getCapabilitiesForFamily(family)
    }

    /**
     * Retrieves detailed information about a specific model.
     */
    private suspend fun getModelDetails(modelName: String): OllamaShowModelResponse? {
        return try {
            client.post("$baseUrl/api/show") {
                contentType(ContentType.Application.Json)
                setBody(OllamaShowModelRequest(name = modelName))
            }.body<OllamaShowModelResponse>()
        } catch (e: Exception) {
            logger.debug(e) { "Failed to get details for model: $modelName" }
            null
        }
    }

    /**
     * Determines if a model is primarily for embeddings - using utility function.
     */
    private fun isEmbeddingModel(name: String): Boolean {
        return OllamaModelUtils.isEmbeddingModel(name)
    }

    /**
     * Pulls a model from the Ollama registry.
     */
    public suspend fun pullModel(modelName: String): Boolean {
        return try {
            val response = client.post("$baseUrl/api/pull") {
                contentType(ContentType.Application.Json)
                setBody(OllamaPullModelRequest(name = modelName, stream = false))
            }.body<OllamaPullModelResponse>()
            
            logger.info { "Model pull status: ${response.status}" }
            response.status.contains("success", ignoreCase = true)
        } catch (e: Exception) {
            logger.error(e) { "Failed to pull model: $modelName" }
            false
        }
    }

    /**
     * Creates a dynamic LLModel for any Ollama model name - purely dynamic.
     */
    public suspend fun createDynamicModel(modelName: String): LLModel {
        // Try to resolve from available models first
        resolveModel(modelName)?.let { return it }
        
        // Create a model with basic capabilities as fallback
        val family = OllamaModelUtils.extractModelFamily(modelName)
        val capabilities = getCapabilitiesForFamily(family)
        
        return LLModel(
            provider = LLMProvider.Ollama,
            id = modelName,
            capabilities = capabilities,
        )
    }

    /**
     * Invalidates the model cache, forcing a refresh on next access.
     */
    public fun invalidateCache() {
        modelsCache = null
        lastCacheUpdate = 0
    }
}
