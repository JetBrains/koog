package ai.koog.prompt.executor.selection

import ai.koog.prompt.executor.selection.ModelFilterAPI.Companion.decide
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel

/**
 * Builder for constructing a [ModelSelector] with filters and rankers.
 */
public class DefaultModelSelectorBuilder {

    private val filters: MutableList<ModelFilter> = mutableListOf()
    private val rankers: MutableList<ModelRanker> = mutableListOf()
    private var maxConcurrentlyFilteredModels: Int = DefaultModelSelector.DEFAULT_MAX_CONCURRENTLY_FILTERED_MODELS

    /**
     * Builds the configured [ModelSelector].
     */
    public fun build(): ModelSelector =
        DefaultModelSelector(
            filters = filters.toList(),
            rankers = rankers.toList(),
            maxConcurrentlyFilteredModels = maxConcurrentlyFilteredModels,
        )

    /**
     * Adds a custom [ModelFilter].
     */
    public fun withFilter(filter: ModelFilter): DefaultModelSelectorBuilder = apply {
        filters += filter
    }

    /**
     * Adds a filter defined by a boolean predicate lambda.
     */
    public fun withFilter(filterLambda: (LLModel) -> Boolean): DefaultModelSelectorBuilder = apply {
        filters += ModelFilter { decide(filterLambda(it)) }
    }

    /**
     * Adds a custom [ModelRanker].
     */
    public fun withRanker(ranker: ModelRanker): DefaultModelSelectorBuilder = apply {
        rankers += ranker
    }

    /**
     * Adds a ranker defined by a lambda.
     */
    public fun withRanker(rankerLambda: (List<LLModel>) -> Ranking): DefaultModelSelectorBuilder = apply {
        rankers += ModelRanker(rankerLambda)
    }

    /**
     * Adds a filter that accepts only models supporting all [capabilities].
     */
    public fun withCapabilities(vararg capabilities: LLMCapability): DefaultModelSelectorBuilder = apply {
        filters += ModelFilters.withCapabilities(*capabilities)
    }

    /**
     * Adds a filter that accepts only models with a context window of at least [minTokens].
     */
    public fun withMinContextLength(minTokens: Long): DefaultModelSelectorBuilder = apply {
        filters += ModelFilters.withMinContextLength(minTokens)
    }

    /**
     * Adds a ranker that prefers models with the largest context window.
     */
    public fun withBiggestContextLength(): DefaultModelSelectorBuilder = apply {
        rankers += ModelRankers.biggestContextLength()
    }

    /**
     * Adds a ranker that prefers models with the highest maximum output token count.
     */
    public fun withMostOutputTokens(): DefaultModelSelectorBuilder = apply {
        rankers += ModelRankers.mostOutputTokens()
    }

    /**
     * Sets the maximum number of models evaluated concurrently during filtering.
     */
    public fun withMaxConcurrentlyFilteredModels(max: Int): DefaultModelSelectorBuilder = apply {
        require(max > 0) { "max must be greater than 0." }
        maxConcurrentlyFilteredModels = max
    }
}
