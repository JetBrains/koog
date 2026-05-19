package ai.koog.prompt.executor.model

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.structure.json.generator.BasicJsonSchemaGenerator
import ai.koog.prompt.structure.json.generator.StandardJsonSchemaGenerator
import kotlinx.coroutines.flow.Flow

/**
 * Represents the type of LLM operation being performed.
 * Used by [PromptExecutorAPI.resolveModel] to allow operation-specific model substitution.
 */
public sealed class LLMOperation {
    public object Execute : LLMOperation()
    public object Streaming : LLMOperation()
    public object Moderate : LLMOperation()
}

/**
 * API for [PromptExecutor].
 *
 * Implements the Template Method pattern: [execute], [streaming], and [moderate] are concrete
 * final methods that resolve the effective model via [resolveModel], then delegate to the
 * abstract hooks [onExecute], [onStreaming], and [onModerate]. Subclasses implement the hooks
 * and optionally override [resolveModel] for fallback or routing logic.
 */
public abstract class PromptExecutorAPI : AutoCloseable {

    /**
     * Resolves the effective model to use for the given operation. Returns [model] unchanged by default.
     *
     * Override in executors that transparently substitute the requested model (e.g. fallback on
     * provider unavailability, load-balanced routing). The resolved model is what gets passed to
     * [onExecute], [onStreaming], and [onModerate], so pipeline events always reflect the actual
     * model used.
     */
    public open fun resolveModel(
        model: LLModel,
        tools: List<ToolDescriptor>,
        operation: LLMOperation
    ): LLModel = model

    /**
     * Executes a given prompt using the specified LLM and tools, returning a list of responses.
     *
     * Resolves the effective model via [resolveModel] then delegates to [onExecute].
     */
    public suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor> = emptyList()
    ): List<Message.Response> {
        val effectiveModel = resolveModel(model, tools, LLMOperation.Execute)
        return onExecute(prompt, effectiveModel, tools)
    }

    /**
     * Executes a given prompt using the specified LLM and returns a stream of [StreamFrame] objects.
     *
     * Resolves the effective model via [resolveModel] then delegates to [onStreaming].
     */
    public fun streaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor> = emptyList()
    ): Flow<StreamFrame> {
        val effectiveModel = resolveModel(model, tools, LLMOperation.Streaming)
        return onStreaming(prompt, effectiveModel, tools)
    }

    /**
     * Moderates the content of a given prompt using the specified LLM.
     *
     * Resolves the effective model via [resolveModel] then delegates to [onModerate].
     */
    public suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
        val effectiveModel = resolveModel(model, emptyList(), LLMOperation.Moderate)
        return onModerate(prompt, effectiveModel)
    }

    /**
     * Receives multiple independent choices from the LLM. Default implementation wraps [execute].
     * Override in executors that natively support multiple choices.
     */
    public open suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<LLMChoice> = listOf(execute(prompt, model, tools))

    /**
     * Implementation hook for [execute]. Always receives the effective model after [resolveModel].
     */
    public abstract suspend fun onExecute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor> = emptyList()
    ): List<Message.Response>

    /**
     * Implementation hook for [streaming]. Always receives the effective model after [resolveModel].
     */
    public abstract fun onStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor> = emptyList()
    ): Flow<StreamFrame>

    /**
     * Implementation hook for [moderate]. Always receives the effective model after [resolveModel].
     */
    public abstract suspend fun onModerate(
        prompt: Prompt,
        model: LLModel
    ): ModerationResult

    /**
     * Retrieves a list of available models from all LLM clients managed by this executor.
     */
    public open suspend fun models(): List<LLModel> {
        throw UnsupportedOperationException("Not implemented for this executor")
    }

    /**
     * Standard JSON schema generator required for the given model.
     * Returns [StandardJsonSchemaGenerator] by default.
     */
    public open fun getStandardJsonSchemaGenerator(model: LLModel): StandardJsonSchemaGenerator {
        return StandardJsonSchemaGenerator
    }

    /**
     * Basic JSON schema generator required for the given model.
     * Returns [BasicJsonSchemaGenerator] by default.
     */
    public open fun getBasicJsonSchemaGenerator(model: LLModel): BasicJsonSchemaGenerator {
        return BasicJsonSchemaGenerator
    }
}
