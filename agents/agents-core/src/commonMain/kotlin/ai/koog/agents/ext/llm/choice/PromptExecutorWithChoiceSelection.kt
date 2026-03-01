package ai.koog.agents.ext.llm.choice

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.Flow

/**
 * A specialized implementation of `PromptExecutor` that enhances the standard execution process
 * by introducing a choice selection mechanism. This class acts as a proxy that intercepts
 * the standard execute method, generates multiple response choices, and applies a selection
 * strategy to filter and choose the most appropriate responses.
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
public class PromptExecutorWithChoiceSelection(
    private val executor: PromptExecutor,
    private val choiceSelectionStrategy: ChoiceSelectionStrategy,
) : PromptExecutor() {
    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
        val choices = executor.executeMultipleChoices(prompt, model, tools)

        return choiceSelectionStrategy.choose(prompt, choices)
    }

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): Flow<StreamFrame> = executor.executeStreaming(prompt, model, tools)

    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel
    ): ModerationResult = executor.moderate(prompt, model)

    override fun close(): Unit = executor.close()

    override suspend fun models(): List<LLModel> = executor.models()

    override suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<LLMChoice> = executor.executeMultipleChoices(prompt, model, tools)
}
