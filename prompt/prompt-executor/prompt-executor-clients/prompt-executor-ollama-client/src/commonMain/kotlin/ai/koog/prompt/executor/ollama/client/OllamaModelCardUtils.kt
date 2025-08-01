package ai.koog.prompt.executor.ollama.client

import ai.koog.prompt.llm.LLMCapability

/**
 * Finds a model card by name.
 */
public fun List<OllamaModelCard>.findByNameOrNull(name: String): OllamaModelCard? =
    firstOrNull { it.name.isSameModelAs(name) }

internal fun String.isSameModelAs(name: String): Boolean =
    ':' in name && this == name || this.withoutTag == name

internal val String.withoutTag: String get() = substringBeforeLast(':')

/**
 * Filters a list of model cards by the given criteria.
 * @param family the family, or null if you don't want to filter by family
 * @param maxSize the maximum size, or null if you don't want to filter by size
 * @param minParameterCount the minimum parameter count, or null if you don't want to filter by parameter count
 * @param minContextLength the minimum context length, or null if you don't want to filter by context length
 * @param minEmbeddingLength the minimum embedding length, or null if you don't want to filter by embedding length
 * @param requiredCapabilities the required capabilities, or null if you don't want to filter by capabilities
 */
public fun List<OllamaModelCard>.findBestSuitedModels(
    family: String? = null,
    maxSize: Long? = null,
    minParameterCount: Long? = null,
    minContextLength: Long? = null,
    minEmbeddingLength: Long? = null,
    requiredCapabilities: List<LLMCapability>? = null,
): List<OllamaModelCard> = filter { card ->
    (family?.let { card.family == it } ?: true) &&
        (maxSize?.let { card.size <= it } ?: true) &&
        (minParameterCount?.let { card.parameterCount != null && card.parameterCount >= it } ?: true) &&
        (minContextLength?.let { card.contextLength != null && card.contextLength >= it } ?: true) &&
        (minEmbeddingLength?.let { card.embeddingLength != null && card.embeddingLength >= it } ?: true) &&
        (requiredCapabilities?.let { card.capabilities.containsAll(it) } ?: true)
}
