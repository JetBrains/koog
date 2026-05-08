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

/**
 * Hook invoked for prompt execution lifecycle events.
 *
 * Hook invocation is part of the executor call path: [handle] may suspend, and the executor must not continue past
 * the lifecycle point being reported until [handle] returns.
 */
public fun interface PromptExecutorHook {
    public suspend fun handle(event: PromptExecutorEvent)

    public companion object {
        public val NoOp: PromptExecutorHook = PromptExecutorHook { }
    }
}

/**
 * Per-call context passed to [HookablePromptExecutor] implementations.
 *
 * The context provides a stable [promptExecutionId] for correlating lifecycle events from the same executor operation
 * and an [executorHook] hook for ordered lifecycle handling.
 */
public data class PromptExecutionContext(
    @OptIn(ExperimentalUuidApi::class)
    public val promptExecutionId: String = Uuid.random().toString(),

    /**
     * Hook for lifecycle events emitted during this prompt execution.
     */
    public val executorHook: PromptExecutorHook = PromptExecutorHook.NoOp,
) {

    /**
     * Handles [event] through [executorHook] after verifying that it belongs to this prompt execution.
     */
    public suspend fun handle(event: PromptExecutorEvent) {
        require(event.promptExecutionId == promptExecutionId) {
            "Event context mismatch: expected $promptExecutionId, got ${event.promptExecutionId}"
        }
        executorHook.handle(event)
    }
}

/**
 * A [PromptExecutor] whose implementations can report lifecycle events through a per-call hook.
 *
 * Base [PromptExecutor] methods create a default [PromptExecutionContext] and delegate to corresponding methods supporting [PromptExecutionContext] based hookable execution.
 * Callers that need ordered lifecycle handling can use the overloads that accept [PromptExecutionContext] and provide a [PromptExecutorHook].
 *
 * Implementations should emit lifecycle events at the appropriate execution points by calling
 * [PromptExecutionContext.handle]. Hook calls are synchronous with the executor operation and may suspend.
 *
 * Subclasses are expected to implement the context-taking methods declared on this class and emit the corresponding
 * [PromptExecutorEvent]s through [PromptExecutionContext.handle].
 */
public abstract class HookablePromptExecutor : PromptExecutor() {

    public abstract suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        context: PromptExecutionContext,
    ): List<Message.Response>

    public abstract fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        context: PromptExecutionContext,
    ): Flow<StreamFrame>

    public abstract suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        context: PromptExecutionContext,
    ): List<LLMChoice>

    public abstract suspend fun moderate(
        prompt: Prompt,
        model: LLModel,
        context: PromptExecutionContext,
    ): ModerationResult

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<Message.Response> = execute(prompt, model, tools, PromptExecutionContext())

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): Flow<StreamFrame> = executeStreaming(prompt, model, tools, PromptExecutionContext())

    override suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<LLMChoice> = executeMultipleChoices(prompt, model, tools, PromptExecutionContext())

    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel
    ): ModerationResult = moderate(prompt, model, PromptExecutionContext())
}
