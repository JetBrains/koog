package ai.koog.prompt.executor.model

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

public data class PromptExecutionContext(
    @OptIn(ExperimentalUuidApi::class)
    public val promptExecutionId: String = Uuid.random().toString(),
)

/**
 * A [PromptExecutor] that emits [PromptExecutorEvent] instances and accepts caller-provided execution context.
 *
 * The legacy [PromptExecutor] methods are final and delegate to context-aware counterparts, so observable executor
 * implementations only need to implement methods that receive [PromptExecutionContext].
 */
public abstract class ObservablePromptExecutor : PromptExecutor() {

    /**
     * Events emitted by this executor.
     */
    public abstract val events: Flow<PromptExecutorEvent>

    public abstract suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        promptExecutionContext: PromptExecutionContext,
    ): List<Message.Response>

    public abstract fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        promptExecutionContext: PromptExecutionContext,
    ): Flow<StreamFrame>

    public abstract suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        promptExecutionContext: PromptExecutionContext,
    ): List<LLMChoice>

    public abstract suspend fun moderate(
        prompt: Prompt,
        model: LLModel,
        promptExecutionContext: PromptExecutionContext,
    ): ModerationResult

    final override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<Message.Response> = execute(prompt, model, tools, PromptExecutionContext())

    final override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): Flow<StreamFrame> = executeStreaming(prompt, model, tools, PromptExecutionContext())

    final override suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<LLMChoice> = executeMultipleChoices(prompt, model, tools, PromptExecutionContext())

    final override suspend fun moderate(
        prompt: Prompt,
        model: LLModel
    ): ModerationResult = moderate(prompt, model, PromptExecutionContext())
}
