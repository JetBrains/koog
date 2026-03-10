package ai.koog.prompt.executor.model

import ai.koog.prompt.llm.LLModel

/**
 * Selects and ranks models from a caller-provided model list.
 */
public fun interface ModelSelector {
    /**
     * Produces model selection result for [models], ordered from best to worst.
     *
     * The returned [ModelSelection] must be a subset of [models] — implementations must not
     * introduce models that were not present in the input.
     *
     * @param models Candidate models to select from.
     * @return Selection result with ranked models, all drawn from [models].
     */
    public suspend fun select(models: List<LLModel>): ModelSelection
}

/**
 * Final selector result containing models ordered from best to worst.
 *
 * @constructor Creates selection result from [ranked].
 * @property ranked Selected models ranked from best to worst.
 */
public data class ModelSelection(
    val ranked: List<LLModel>,
) {

    public companion object {
        /**
         * Empty selection result.
         */
        public val EMPTY: ModelSelection = ModelSelection(emptyList())

        /**
         * Creates [ModelSelection] containing only [model].
         */
        public fun single(model: LLModel): ModelSelection = ModelSelection(listOf(model))
    }
}

/**
 * Marker type for model selection pipeline steps.
 */
public sealed interface ModelSelectionStep

/**
 * Factory helpers for creating common [ModelSelector] instances.
 */
public object ModelSelectors {

    /**
     * Builds selector that accepts only [model].
     *
     * [model] must be present in the candidate list passed to [ModelSelector.select];
     * if it is absent, the selection result will be empty.
     *
     * @param model The only accepted model.
     * @return Selector configured with [ModelFilters.specific].
     */
    public fun specific(model: LLModel): ModelSelector =
        withSteps(ModelFilters.specific(model))

    /**
     * Builds selector from [steps], executed by [DefaultModelSelector].
     *
     * Step evaluation is phase-based:
     * - all [ModelFilter] steps are applied first;
     * - then all [ModelRanker] steps are applied in their relative order.
     *
     * @param steps Ordered selection steps to execute.
     * @param maxConcurrentlyFilteredModels Maximum number of models filtered concurrently.
     * @return Selector backed by [DefaultModelSelector].
     */
    public fun withSteps(
        vararg steps: ModelSelectionStep,
        maxConcurrentlyFilteredModels: Int = DefaultModelSelector.DEFAULT_MAX_CONCURRENTLY_FILTERED_MODELS,
    ): ModelSelector =
        DefaultModelSelector(
            steps = steps,
            maxConcurrentlyFilteredModels = maxConcurrentlyFilteredModels,
        )
}

/**
 * Hard filter step that accepts or rejects a model.
 */
public fun interface ModelFilter : ModelSelectionStep {
    /**
     * Evaluates model against filter criteria.
     *
     * @param model Model to evaluate.
     * @return Binary filter decision.
     */
    public suspend fun evaluate(model: LLModel): Decision

    /**
     * Filter decision result.
     */
    public enum class Decision {
        ACCEPTED,
        REJECTED,
    }
}

/**
 * Soft ranking step that orders accepted models into priority buckets.
 */
public fun interface ModelRanker : ModelSelectionStep {
    /**
     * Ranks [models] into ordered buckets from best to worst.
     *
     * @param models Input models to rank, must not contain duplicates.
     * @return Ranking buckets ordered from best to worst, contains the same elements as [models].
     */
    public suspend fun rank(models: List<LLModel>): Ranking
}

/**
 * A single ranking bucket containing models with equal priority.
 *
 * @constructor Creates rank bucket from [models].
 * @property models Models in this bucket.
 */
public data class RankBucket(val models: List<LLModel>) {
    /**
     * Convenience constructor for creating a bucket from vararg [models].
     *
     * @param models Models in this bucket.
     */
    public constructor(vararg models: LLModel) : this(models.toList())

    /**
     * Number of models in this bucket.
     */
    public val size: Int = models.size

    /**
     * `true` when bucket contains more than one model.
     */
    public fun hasTie(): Boolean = size > 1
}

/**
 * Ordered ranking represented as list of non-empty buckets.
 *
 * @constructor Creates ranking from [buckets]. Empty buckets are not allowed.
 * @property buckets Ordered non-empty ranking buckets.
 * @throws IllegalArgumentException If any bucket is empty.
 */
public data class Ranking(val buckets: List<RankBucket>) {

    init {
        require(buckets.all { it.size > 0 }) {
            "All buckets must have at least one model."
        }
    }

    /**
     * Convenience constructor for creating ranking from vararg [buckets].
     *
     * @param buckets Ordered non-empty ranking buckets.
     */
    public constructor(vararg buckets: RankBucket) : this(buckets.toList())

    /**
     * Number of buckets in this ranking.
     */
    public val size: Int = buckets.size

    /**
     * `true` when at least one bucket has a tie.
     */
    public fun hasTies(): Boolean = buckets.any { it.hasTie() }
}
