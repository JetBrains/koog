package ai.koog.prompt.executor.ollama.client

import ai.koog.prompt.executor.clients.ollama.OllamaModelCard
import ai.koog.prompt.executor.clients.ollama.findBestSuitedModels
import ai.koog.prompt.executor.clients.ollama.findByNameOrNull
import ai.koog.prompt.llm.LLMCapability

@Deprecated(
    message = "Use ai.koog.prompt.executor.clients.ollama.findByNameOrNull instead.",
    replaceWith = ReplaceWith("ai.koog.prompt.executor.clients.ollama.findByNameOrNull")
)
public fun List<OllamaModelCard>.findByNameOrNull(name: String): OllamaModelCard? = findByNameOrNull(name)

@Deprecated(
    message = "Use ai.koog.prompt.executor.clients.ollama.findBestSuitedModels instead.",
    replaceWith = ReplaceWith("ai.koog.prompt.executor.clients.ollama.findBestSuitedModels")
)
public fun List<OllamaModelCard>.findBestSuitedModels(
    family: String? = null,
    maxSize: Long? = null,
    minParameterCount: Long? = null,
    minContextLength: Long? = null,
    minEmbeddingLength: Long? = null,
    requiredCapabilities: List<LLMCapability>? = null,
): List<OllamaModelCard> = findBestSuitedModels(
    family = family,
    maxSize = maxSize,
    minParameterCount = minParameterCount,
    minContextLength = minContextLength,
    minEmbeddingLength = minEmbeddingLength,
    requiredCapabilities = requiredCapabilities,
)
