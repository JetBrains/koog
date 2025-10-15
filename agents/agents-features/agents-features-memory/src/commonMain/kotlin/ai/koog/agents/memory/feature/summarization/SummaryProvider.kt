package ai.koog.agents.memory.feature.summarization

import ai.koog.agents.memory.model.Fact

/**
 * Produces concise representations of stored facts.
 *
 * Implementations are optional – when absent the system falls back to raw fact values.
 */
public interface SummaryProvider {
    public suspend fun summarize(fact: Fact): SummaryResult
}

/**
 * Result of summarizing a fact.
 *
 * @property summary Short textual representation of the fact.
 * @property keywords Optional keywords to aid retrieval.
 */
public data class SummaryResult(
    val summary: String?,
    val keywords: List<String> = emptyList()
)
