@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.prompt.executor.selection

import ai.koog.prompt.llm.LLModel

public actual abstract class ModelFilter actual constructor() : ModelFilterAPI {

    public companion object {
        public operator fun invoke(filter: (LLModel) -> ModelFilterAPI.Decision): ModelFilter = object : ModelFilter() {
            override suspend fun evaluate(model: LLModel): ModelFilterAPI.Decision = filter(model)
        }
    }
}
