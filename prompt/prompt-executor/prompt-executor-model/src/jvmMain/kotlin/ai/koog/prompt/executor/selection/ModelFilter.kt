@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(InternalPromptAPI::class)

package ai.koog.prompt.executor.selection

import ai.koog.agents.annotations.JavaAPI
import ai.koog.prompt.annotations.InternalPromptAPI
import ai.koog.prompt.execution.utils.runOnIOBoundDispatcher
import ai.koog.prompt.executor.selection.ModelFilterAPI.Decision
import ai.koog.prompt.llm.LLModel
import java.util.concurrent.ExecutorService

public actual abstract class ModelFilter actual constructor() : ModelFilterAPI {

    @JavaAPI
    @JvmOverloads
    public fun evaluate(
        model: LLModel,
        executorService: ExecutorService? = null
    ): Decision = runOnIOBoundDispatcher(executorService) {
        evaluate(model)
    }
}
