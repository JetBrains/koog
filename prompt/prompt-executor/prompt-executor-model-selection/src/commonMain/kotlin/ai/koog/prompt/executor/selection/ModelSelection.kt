package ai.koog.prompt.executor.selection

import ai.koog.prompt.llm.LLModel
import kotlin.jvm.JvmStatic

/**
 * Selects and ranks models from a caller-provided candidate list.
 *
 * Implementations should return only models that were present in the input list.
 */
public abstract class ModelSelector {

    /**
     * Produces a model selection result for [models], ordered from best to worst.
     * @param models List of candidate models to select from.
     */
    public abstract suspend fun select(models: List<LLModel>): ModelSelection

    public companion object {
        /**
         * Creates a [ModelSelector] from a suspend lambda.
         */
        public operator fun invoke(selector: suspend (List<LLModel>) -> ModelSelection): ModelSelector =
            object : ModelSelector() {
                override suspend fun select(models: List<LLModel>): ModelSelection = selector(models)
            }

        /**
         * Returns a selector that accepts only [model] and rejects all others.
         */
        @JvmStatic
        public fun specific(model: LLModel): ModelSelector =
            ModelSelector { models ->
                if (model in models) {
                    ModelSelection.single(model)
                } else {
                    ModelSelection.EMPTY
                }
            }
    }
}

/**
 * Final selector result containing models ordered from best to worst.
 * @property ranked List of models ordered from best to worst.
 */
public data class ModelSelection(public val ranked: List<LLModel>) {
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
