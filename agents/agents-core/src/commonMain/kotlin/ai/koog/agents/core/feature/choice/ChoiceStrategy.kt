package ai.koog.agents.core.feature.choice

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.LLMChoice

/**
 * Represents a strategy for selecting a choice from a list of available options
 * for a given prompt.
 */
public interface ChoiceStrategy {
    /**
     * Selects one choice from a list of available `LLMChoice` options based on a given prompt.
     *
     * @param prompt The `Prompt` containing the context or query for which a choice should be selected.
     * @param choices A list of `LLMChoice` options from which one will be chosen.
     * @return The selected `LLMChoice` from the provided list based on the implemented strategy.
     */
    public suspend fun choose(prompt: Prompt, choices: List<LLMChoice>): LLMChoice
}