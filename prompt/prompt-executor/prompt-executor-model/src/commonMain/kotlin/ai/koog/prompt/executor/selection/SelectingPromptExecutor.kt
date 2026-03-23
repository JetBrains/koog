@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.prompt.executor.selection

import ai.koog.prompt.executor.model.PromptExecutor

/**
 * Abstract base for executors that support model selection via [ModelSelector].
 *
 */
public expect abstract class SelectingPromptExecutor() : PromptExecutor, SelectingPromptExecutorAPI
