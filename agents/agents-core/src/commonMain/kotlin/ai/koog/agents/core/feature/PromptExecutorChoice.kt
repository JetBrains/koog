package ai.koog.agents.core.feature

import ai.koog.agents.core.feature.choice.ChoiceStrategy
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message

public class PromptExecutorChoice(
    private val executor: PromptExecutor,
    private val choiceStrategy: ChoiceStrategy,
) : PromptExecutor by executor {
    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
        val choices = executor.executeMultipleChoices(prompt, model, tools)

        return choiceStrategy.choose(prompt, choices)
    }
}