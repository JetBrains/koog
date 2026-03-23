@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.prompt.executor.selection

import ai.koog.prompt.executor.model.PromptExecutor

public actual abstract class SelectingPromptExecutor actual constructor() :
    PromptExecutor(), SelectingPromptExecutorAPI
