@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.prompt.executor.selection

import ai.koog.prompt.llm.LLModel
import kotlin.jvm.JvmStatic

/**
 * Selects and ranks models from a caller-provided candidate list.
 *
 * The returned [ModelSelection] must be a subset of the input — implementations must not
 * introduce models that were not present in the candidate list.
 */
public fun interface ModelSelectorAPI {
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
 * Abstract base class for custom model selectors.
 *
 * Subclass this to implement a fully custom selection strategy. For most cases [DefaultModelSelector] should be
 * sufficient.
 */
public expect abstract class ModelSelector() : ModelSelectorAPI

/**
 * Creates a [ModelSelector] from a suspend lambda.
 *
 * @param selector Suspend lambda that produces a [ModelSelection] from the candidate models.
 *   Must return only models drawn from the input list.
 */
public fun ModelSelector(selector: suspend (List<LLModel>) -> ModelSelection): ModelSelector =
    object : ModelSelector() {
        override suspend fun select(models: List<LLModel>): ModelSelection = selector(models)
    }

/**
 * Factory functions for common [ModelSelector] instances.
 */
public object ModelSelectors {

    /**
     * Returns a selector that accepts only [model] and rejects all others.
     *
     * @param model The single model to select.
     */
    @JvmStatic
    public fun specific(model: LLModel): ModelSelector =
        DefaultModelSelector(filters = listOf(ModelFilters.specific(model)))
}

/**
 * Final selector result containing models ordered from best to worst.
 *
 * @constructor Creates a selection result from [ranked].
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
