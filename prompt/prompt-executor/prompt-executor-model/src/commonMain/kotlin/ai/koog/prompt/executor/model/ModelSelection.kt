package ai.koog.prompt.executor.model

import ai.koog.prompt.llm.LLModel

public fun interface ModelSelector {
    public suspend fun select(models: List<LLModel>): ModelSelection
}

public data class ModelSelection(
    val ranked: List<LLModel>,
) {

    public companion object {
        public val EMPTY: ModelSelection = ModelSelection(emptyList())
    }
}

public sealed interface ModelSelectionStep

public object ModelSelectors {
    public fun specific(model: LLModel): ModelSelector =
        withSteps(ModelFilters.specific(model))

    public fun withSteps(
        vararg steps: ModelSelectionStep,
        maxConcurrentlyFilteredModels: Int = 8,
    ): ModelSelector =
        DefaultModelSelector(
            steps = steps.toList(),
            maxConcurrentlyFilteredModels = maxConcurrentlyFilteredModels,
        )
}

public fun interface ModelFilter : ModelSelectionStep {
    public suspend fun evaluate(model: LLModel): Decision

    public enum class Decision {
        ACCEPTED,
        REJECTED,
    }
}

public fun interface ModelRanker : ModelSelectionStep {
    public suspend fun rank(models: List<LLModel>): Ranking
}

public data class RankBucket(val models: List<LLModel>){

    public constructor(vararg models: LLModel) : this(models.toList())

    public val size: Int = models.size

    public fun hasTie(): Boolean = size > 1
}

public data class Ranking(val buckets: List<RankBucket>){

    init {
        require(buckets.all { it.size > 0 }){
            "All buckets must have at least one model."
        }
    }

    public constructor(vararg buckets: RankBucket) : this(buckets.toList())

    public val size: Int = buckets.size

    public fun hasTies(): Boolean = buckets.any { it.hasTie() }
}
