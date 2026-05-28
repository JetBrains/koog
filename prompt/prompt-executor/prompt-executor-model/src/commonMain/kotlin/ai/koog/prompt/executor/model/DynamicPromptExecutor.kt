package ai.koog.prompt.executor.model

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * A [PromptExecutor] that introduces an explicit [resolveModel] step before every LLM operation.
 *
 * Subclasses use [resolveModel] to translate a caller-requested [LLModel] into a [ResolvedModel]
 * — for example, to swap in a fallback model when the requested one is not directly available.
 * Each [PromptExecutor] entry point taking a plain [LLModel] is finalized here and delegates
 * to the matching [ResolvedModel]-based overload, so model resolution always happens exactly
 * once per call and subclasses cannot bypass it.
 *
 * Subclasses must implement [resolveModel] together with [execute], [executeStreaming],
 * [executeMultipleChoices], and [moderate] taking a [ResolvedModel].
 */
public abstract class DynamicPromptExecutor : PromptExecutor() {

    //region Model resolution related methods

    /**
     * Resolves [requestedModel] into the [ResolvedModel] to actually use for the given
     * [promptExecutorOperation].
     *
     * This is the single hook for model-resolution policy (e.g. fallback selection). Every entry
     * point of this executor flows through this method exactly once per call.
     */
    abstract override suspend fun resolveModel(
        requestedModel: LLModel,
        promptExecutorOperation: PromptExecutorOperation
    ): ResolvedModel

    /**
     * Executes [prompt] against an already-resolved [resolvedModel].
     *
     * This is the actual extension point for subclasses; the [LLModel] overload of [execute] is
     * finalized and routes here after calling [resolveModel].
     */
    abstract override suspend fun execute(
        prompt: Prompt,
        resolvedModel: ResolvedModel,
        tools: List<ToolDescriptor>
    ): Message.Assistant

    /**
     * Returns multiple independent choices from an already-resolved [resolvedModel].
     *
     * This is the actual extension point for subclasses; the [LLModel] overload of
     * [executeMultipleChoices] is finalized and routes here after calling [resolveModel].
     */
    abstract override suspend fun executeMultipleChoices(
        prompt: Prompt,
        resolvedModel: ResolvedModel,
        tools: List<ToolDescriptor>
    ): LLMChoice

    /**
     * Streams the response for [prompt] against an already-resolved [resolvedModel].
     *
     * This is the actual extension point for subclasses; the [LLModel] overload of
     * [executeStreaming] is finalized and routes here after calling [resolveModel].
     */
    abstract override fun executeStreaming(
        prompt: Prompt,
        resolvedModel: ResolvedModel,
        tools: List<ToolDescriptor>
    ): Flow<StreamFrame>

    /**
     * Moderates [prompt] against an already-resolved [model].
     *
     * This is the actual extension point for subclasses; the [LLModel] overload of [moderate] is
     * finalized and routes here after calling [resolveModel].
     */
    abstract override suspend fun moderate(prompt: Prompt, model: ResolvedModel): ModerationResult

    //endregion
    //region Basic Prompt Executor overrides delegating to ResolvedModel-based counterparts

    /**
     * Finalized to enforce that every call resolves through [resolveModel] before dispatch.
     * Subclasses customize behavior by overriding the [ResolvedModel]-based [execute] overload.
     */
    final override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): Message.Assistant = execute(prompt, resolveModel(model, PromptExecutorOperation.Execute), tools)

    /**
     * Finalized to enforce that every call resolves through [resolveModel] before dispatch.
     * Subclasses customize behavior by overriding the [ResolvedModel]-based [executeStreaming]
     * overload.
     */
    final override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): Flow<StreamFrame> = flow {
        emitAll(executeStreaming(prompt, resolveModel(model, PromptExecutorOperation.Stream), tools))
    }

    /**
     * Finalized to enforce that every call resolves through [resolveModel] before dispatch.
     * Subclasses customize behavior by overriding the [ResolvedModel]-based [moderate] overload.
     */
    final override suspend fun moderate(
        prompt: Prompt,
        model: LLModel
    ): ModerationResult = moderate(prompt, resolveModel(model, PromptExecutorOperation.Moderate))

    /**
     * Finalized to enforce that every call resolves through [resolveModel] before dispatch.
     * Subclasses customize behavior by overriding the [ResolvedModel]-based
     * [executeMultipleChoices] overload.
     */
    final override suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): LLMChoice = executeMultipleChoices(prompt, resolveModel(model, PromptExecutorOperation.MultipleChoices), tools)

    //endregion
}
