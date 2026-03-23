@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(InternalPromptAPI::class)

package ai.koog.prompt.executor.selection

import ai.koog.agents.annotations.JavaAPI
import ai.koog.prompt.annotations.InternalPromptAPI
import ai.koog.prompt.execution.utils.runOnIOBoundDispatcher
import ai.koog.prompt.llm.LLModel
import java.util.concurrent.ExecutorService

public actual abstract class ModelRanker actual constructor() : ModelRankerAPI {

    @JavaAPI
    @JvmOverloads
    public fun rank(
        models: List<LLModel>,
        executorService: ExecutorService? = null
    ): Ranking = runOnIOBoundDispatcher(executorService) {
        rank(models)
    }
}
