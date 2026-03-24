@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.prompt.executor.selection

import ai.koog.prompt.llm.LLModel

public actual abstract class ModelRanker actual constructor() : ModelRankerAPI {

    public companion object {
        public operator fun invoke(ranker: (List<LLModel>) -> Ranking): ModelRanker = object : ModelRanker() {
            override suspend fun rank(models: List<LLModel>): Ranking = ranker(models)
        }
    }
}
