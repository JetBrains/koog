@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(InternalPromptAPI::class)

package ai.koog.prompt.executor.selection

import ai.koog.agents.annotations.JavaAPI
import ai.koog.prompt.annotations.InternalPromptAPI
import ai.koog.prompt.execution.utils.runOnIOBoundDispatcher
import ai.koog.prompt.llm.LLModel
import java.util.concurrent.ExecutorService

public actual abstract class ModelSelector actual constructor() : ModelSelectorAPI {

    /**
     * Blocking variant of [ModelSelectorAPI.select] for Java callers.
     *
     * @param models Candidate models to select from.
     * @param executorService Optional executor for running the coroutine. Uses the default IO dispatcher when `null`.
     * @return Selection result with ranked models.
     */
    @JavaAPI
    @JvmOverloads
    public fun select(
        models: List<LLModel>,
        executorService: ExecutorService? = null
    ): ModelSelection = runOnIOBoundDispatcher(executorService) {
        select(models)
    }

    public companion object {
        /**
         * Returns a [ModelSelectorBuilder] for constructing a selector with filters and rankers.
         *
         * Prefer using the [ModelSelectorBuilder] DSL extension functions on [SelectingPromptExecutor]
         * in Kotlin. This factory is primarily intended for Java callers.
         */
        @JvmStatic
        public fun builder(): ModelSelectorBuilder = ModelSelectorBuilder()
    }
}
