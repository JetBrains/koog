package ai.koog.agents.memory.providers

import ai.koog.agents.memory.model.TokenBudget

/**
 * Carries optional parameters that influence memory operations.
 *
 * - [budget]: maximum facts/tokens to inject for a load.
 * - [query]: similarity query string for ranking.
 * - [enrich]: whether summarisation hooks should run on save.
 */
public data class MemoryRequestOptions(
    val budget: TokenBudget? = null,
    val query: String? = null,
    val enrich: Boolean = true
)
