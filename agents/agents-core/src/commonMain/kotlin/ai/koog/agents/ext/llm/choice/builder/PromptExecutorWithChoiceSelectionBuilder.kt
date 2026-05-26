package ai.koog.agents.ext.llm.choice.builder

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.ext.llm.choice.ChoiceSelectionStrategy
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.model.PromptExecutorBuilder
import ai.koog.prompt.executor.model.PromptExecutorOperation
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.Flow

/**
 * A specialized builder that enhances the standard execution process by introducing a choice
 * selection mechanism. The built [PromptExecutor] acts as a proxy that intercepts the standard
 * execute method, generates multiple response choices, and applies a selection strategy to filter
 * and choose the most appropriate responses.
 *
 * The execution process involves two main steps:
 * 1. Generating multiple response choices using the underlying executor
 * 2. Applying the specified selection strategy to choose the most suitable responses
 *
 * @param executor The underlying `PromptExecutor` responsible for performing the prompt execution
 *                 and generating multiple response choices.
 * @param choiceSelectionStrategy The strategy implementation that defines the logic for
 *                               selecting and filtering the generated response choices.
 */
public class PromptExecutorWithChoiceSelectionBuilder(
    private val executor: PromptExecutor,
    private val choiceSelectionStrategy: ChoiceSelectionStrategy,
) : PromptExecutorBuilder() {

    override fun resolveModel(model: LLModel, operation: PromptExecutorOperation): LLModel =
        executor.resolveModel(model, operation)

    override suspend fun onExecute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Message.Assistant {
        val choices = executor.executeMultipleChoices(prompt, model, tools)
        return choiceSelectionStrategy.choose(prompt, choices)
    }

    override fun onStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Flow<StreamFrame> = executor.executeStreaming(prompt, model, tools)

    override suspend fun onModerate(prompt: Prompt, model: LLModel): ModerationResult =
        executor.moderate(prompt, model)

    override suspend fun onMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): LLMChoice = executor.executeMultipleChoices(prompt, model, tools)

    override suspend fun onModels(): List<LLModel> = executor.models()

    override fun onClose() {
        executor.close()
    }
}
