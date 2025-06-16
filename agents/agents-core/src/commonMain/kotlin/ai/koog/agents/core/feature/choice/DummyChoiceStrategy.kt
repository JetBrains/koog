package ai.koog.agents.core.feature.choice

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.LLMChoice

public class DummyChoiceStrategy : ChoiceStrategy {
    override suspend fun choose(prompt: Prompt, choices: List<LLMChoice>): LLMChoice = choices.first()
}