package ai.koog.prompt.executor.model

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.structure.json.generator.BasicJsonSchemaGenerator
import ai.koog.prompt.structure.json.generator.StandardJsonSchemaGenerator
import kotlinx.coroutines.flow.Flow

/**
 * The single extension point for implementing a [PromptExecutor].
 *
 * Implementors override the `on*` hooks (and optionally [resolveModel]) on this class, and obtain a
 * [PromptExecutor] by calling [build]. The returned [PromptExecutor] has final implementations of
 * [PromptExecutorAPI.execute], [PromptExecutorAPI.executeStreaming], [PromptExecutorAPI.moderate],
 * [PromptExecutorAPI.executeMultipleChoices], and [PromptExecutorAPI.models] that follow a fixed
 * pipeline: first [resolveModel] is called for the requested [PromptExecutorOperation], then the
 * corresponding `on*` hook is invoked with the resolved model.
 *
 * This shape guarantees that decorators (and feature interceptors) always see the model that will
 * actually run, not the one that was originally requested.
 *
 * ### Idempotency contract for [resolveModel]
 *
 * [resolveModel] **must** be idempotent: for any model `m` and operation `op`,
 * `resolveModel(resolveModel(m, op), op) == resolveModel(m, op)`. This lets decorators delegate to
 * an inner [PromptExecutor] without worrying about double resolution — the inner re-resolve is
 * guaranteed to be a no-op.
 */
public abstract class PromptExecutorBuilder {

    /**
     * Resolves the actual model that will be used for [operation].
     *
     * Override to implement provider-aware model substitution (e.g. fallback to a different model
     * when the requested provider has no registered client). Defaults to identity.
     *
     * **Must be idempotent.** See the class-level docs.
     */
    public open fun resolveModel(model: LLModel, operation: PromptExecutorOperation): LLModel = model

    /**
     * Executes [prompt] against the (already-resolved) [model] with [tools]. Required override.
     */
    public abstract suspend fun onExecute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Message.Assistant

    /**
     * Executes [prompt] against the (already-resolved) [model] with [tools] as a stream. Required override.
     */
    public abstract fun onStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Flow<StreamFrame>

    /**
     * Returns multiple independent choices for [prompt] against the (already-resolved) [model].
     * Defaults to a single-choice list wrapping [onExecute].
     */
    public open suspend fun onMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): LLMChoice = listOf(onExecute(prompt, model, tools))

    /**
     * Moderates [prompt] against the (already-resolved) [model]. Required override.
     */
    public abstract suspend fun onModerate(prompt: Prompt, model: LLModel): ModerationResult

    /**
     * Returns the list of available models. Default throws [UnsupportedOperationException].
     */
    public open suspend fun onModels(): List<LLModel> =
        throw UnsupportedOperationException("Not implemented for this executor")

    /**
     * Standard JSON schema generator for [model].
     */
    public open fun getStandardJsonSchemaGenerator(model: LLModel): StandardJsonSchemaGenerator =
        StandardJsonSchemaGenerator

    /**
     * Basic JSON schema generator for [model].
     */
    public open fun getBasicJsonSchemaGenerator(model: LLModel): BasicJsonSchemaGenerator =
        BasicJsonSchemaGenerator

    /**
     * Invoked from [PromptExecutorAPI.close]; default is a no-op.
     */
    public open fun onClose() {}

    /**
     * Builds a [PromptExecutor] backed by this builder's hooks.
     */
    public fun build(): PromptExecutor = PromptExecutor(this)
}
