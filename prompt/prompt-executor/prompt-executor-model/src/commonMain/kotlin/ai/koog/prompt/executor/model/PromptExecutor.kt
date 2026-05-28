@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.prompt.executor.model

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.annotations.InternalPromptAPI
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.structure.json.generator.BasicJsonSchemaGenerator
import ai.koog.prompt.structure.json.generator.StandardJsonSchemaGenerator
import kotlinx.coroutines.flow.Flow
import kotlin.jvm.JvmSynthetic

/**
 * Executes LLM prompts.
 *
 * `PromptExecutor` is the runtime-facing entry point used by [ai.koog.agents.core.agent.AIAgent] and other
 * consumers. Its public methods are final and follow a fixed dispatch pipeline:
 *
 * 1. The requested model is passed through [resolveModel] for the operation about to run.
 * 2. The corresponding `on*` hook on the underlying [PromptExecutorBuilder] is invoked with the
 *    resolved model.
 *
 * To customize executor behavior, subclass [PromptExecutorBuilder] and call its `build()`.
 * Decorators that intercept LLM calls (e.g. for logging, tracing, or pipeline event hooks) can
 * inspect the resolved model via [resolveModel] before forwarding, and will always observe the
 * model that actually runs — never a pre-resolution value.
 *
 * Instances are constructed exclusively via [PromptExecutorBuilder.build]; the constructor is
 * internal to the framework.
 */
public expect class PromptExecutor internal constructor(builder: PromptExecutorBuilder) : PromptExecutorAPI {

    /**
     * The underlying [PromptExecutorBuilder] backing this executor.
     *
     * Marked as internal API: this is exposed so that decorator builders and test utilities can
     * inspect the builder behind a built [PromptExecutor] (e.g., type-checking for a specific
     * builder implementation). Application code should not depend on this property.
     */
    @InternalPromptAPI
    public val builder: PromptExecutorBuilder

    /**
     * Resolves the actual model that will be used for [operation], delegating to the underlying
     * [PromptExecutorBuilder.resolveModel].
     *
     * Decorators should call this before firing any "starting" event so handlers observe the model
     * that will actually execute.
     */
    public fun resolveModel(model: LLModel, operation: PromptExecutorOperation): LLModel

    @JvmSynthetic
    public override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Message.Assistant

    @JvmSynthetic
    public override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Flow<StreamFrame>

    @JvmSynthetic
    public override suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): LLMChoice

    @JvmSynthetic
    public override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult

    @JvmSynthetic
    public override suspend fun models(): List<LLModel>

    public override fun getStandardJsonSchemaGenerator(model: LLModel): StandardJsonSchemaGenerator

    public override fun getBasicJsonSchemaGenerator(model: LLModel): BasicJsonSchemaGenerator

    public override fun close()
}
