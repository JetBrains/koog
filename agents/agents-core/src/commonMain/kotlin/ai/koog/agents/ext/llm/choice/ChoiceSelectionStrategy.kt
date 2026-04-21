package ai.koog.agents.ext.llm.choice

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.message.LLMChoice

/**
 * Represents a strategy for selecting a choice from a list of available options
 * for a given prompt.
 */
public interface ChoiceSelectionStrategy {
    /**
     * Selects one choice from a list of available `LLMChoice` options based on a given prompt.
     *
     * @param prompt The `Prompt` containing the context or query for which a choice should be selected.
     * @param choices A list of `LLMChoice` options from which one will be chosen.
     * @return The selected `LLMChoice` from the provided list based on the implemented strategy.
     */
    public suspend fun choose(prompt: Prompt, choices: List<LLMChoice>): LLMChoice

    /**
     * Represents the default implementation of the `ChoiceSelectionStrategy` interface.
     *
     * This implementation selects the first available choice from the provided list of `LLMChoice` options
     * in response to the given `Prompt`. It is intended to serve as a simple, deterministic strategy for
     * use cases where no specific selection logic is required.
     */
    public object Default : ChoiceSelectionStrategy {
        override suspend fun choose(prompt: Prompt, choices: List<LLMChoice>): LLMChoice = choices.first()
    }
}
