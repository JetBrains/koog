package ai.koog.agents.ext.llm.choice.factory

import ai.koog.agents.ext.llm.choice.ChoiceSelectionStrategy
import ai.koog.agents.ext.llm.choice.builder.PromptExecutorWithChoiceSelectionBuilder
import ai.koog.prompt.executor.model.PromptExecutor

/**
 * Source-compatible factory for [PromptExecutorWithChoiceSelectionBuilder]. Returns a built [PromptExecutor].
 */
@Suppress("FunctionName")
public fun PromptExecutorWithChoiceSelection(
    executor: PromptExecutor,
    choiceSelectionStrategy: ChoiceSelectionStrategy,
): PromptExecutor = PromptExecutorWithChoiceSelectionBuilder(executor, choiceSelectionStrategy).build()
