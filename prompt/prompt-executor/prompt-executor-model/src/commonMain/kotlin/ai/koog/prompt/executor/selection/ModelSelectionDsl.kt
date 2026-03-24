package ai.koog.prompt.executor.selection

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.selection.ModelFilterAPI.Companion.decide
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.Flow

/** DSL marker for the model-selection builder scope. */
@DslMarker
public annotation class ModelSelectionDsl

/**
 * Executes prompt using model-selection DSL.
 *
 * Example:
 * ```
 * promptExecutor.execute(prompt, tools) {
 *     withCapabilities(LLMCapability.Vision.Image, LLMCapability.ToolChoice)
 *     withMinContextLength(100_000)
 *     withMostOutputTokens()
 * }
 * ```
 *
 * One can provide custom filters ([ModelFilter]) and rankers ([ModelRanker]) to the model selection process:
 * ```
 * val gdprCompliant = CustomGdprFilter()
 * val cheapest = CustomCostRanker()
 *
 * promptExecutor.execute(prompt, tools) {
 *     withCapabilities(LLMCapability.Vision.Image, LLMCapability.ToolChoice)
 *     withMinContextLength(100_000)
 *     withFilter(gdprCompliant)
 *     withRanker(cheapest)
 *     withMostOutputTokens()
 * }
 * ```
 */
@ExperimentalSelectionApi
public suspend fun PromptExecutor.execute(
    prompt: Prompt,
    tools: List<ToolDescriptor> = emptyList(),
    selection: ModelSelectorBuilder.() -> Unit,
): List<Message.Response> = execute(
    prompt = prompt,
    modelSelector = ModelSelectorBuilder().apply(selection).build(),
    tools = tools,
)

/**
 * Streams output frames for [prompt] using model-selection DSL.
 *
 * @param prompt Prompt containing messages and generation parameters.
 * @param tools Tools available during execution.
 * @param selection Builder block configuring filters and rankers.
 * @return Stream of model output frames.
 */
@ExperimentalSelectionApi
public fun PromptExecutor.executeStreaming(
    prompt: Prompt,
    tools: List<ToolDescriptor> = emptyList(),
    selection: ModelSelectorBuilder.() -> Unit,
): Flow<StreamFrame> = executeStreaming(
    prompt = prompt,
    modelSelector = ModelSelectorBuilder().apply(selection).build(),
    tools = tools,
)

/**
 * Returns multiple independent choices for [prompt] using model-selection DSL.
 *
 * @param prompt Prompt containing messages and generation parameters.
 * @param tools Tools available during execution.
 * @param selection Builder block configuring filters and rankers.
 * @return Generated model choices.
 */
@ExperimentalSelectionApi
public suspend fun PromptExecutor.executeMultipleChoices(
    prompt: Prompt,
    tools: List<ToolDescriptor> = emptyList(),
    selection: ModelSelectorBuilder.() -> Unit,
): List<LLMChoice> = executeMultipleChoices(
    prompt = prompt,
    modelSelector = ModelSelectorBuilder().apply(selection).build(),
    tools = tools,
)

/**
 * Moderates [prompt] using model-selection DSL.
 *
 * @param prompt Prompt containing content to moderate.
 * @param selection Builder block configuring filters and rankers.
 * @return Moderation result.
 */
@ExperimentalSelectionApi
public suspend fun PromptExecutor.moderateWithSelection(
    prompt: Prompt,
    selection: ModelSelectorBuilder.() -> Unit,
): ModerationResult = moderate(
    prompt = prompt,
    modelSelector = ModelSelectorBuilder().apply(selection).build()
)

/**
 * Builder for constructing a [ModelSelector] with filters and rankers.
 *
 * Filters are applied first (hard constraints); rankers are then applied lexicographically
 * to the accepted models (soft ordering, with each ranker resolving ties left by the previous one).
 */
@ModelSelectionDsl
public class ModelSelectorBuilder {
    private val filters: MutableList<ModelFilter> = mutableListOf()
    private val rankers: MutableList<ModelRanker> = mutableListOf()
    private var maxConcurrentlyFilteredModels: Int = DefaultModelSelector.DEFAULT_MAX_CONCURRENTLY_FILTERED_MODELS

    /**
     * Adds a custom [ModelFilter] to the filter pipeline.
     *
     * @param filter Filter to add.
     */
    public fun withFilter(filter: ModelFilter): ModelSelectorBuilder = apply {
        filters += filter
    }

    /**
     * Adds a filter defined by a boolean predicate lambda.
     *
     * @param filterLambda Predicate returning `true` to accept a model, `false` to reject it.
     */
    public fun withFilter(filterLambda: (LLModel) -> Boolean): ModelSelectorBuilder = apply {
        filters += ModelFilter { decide(filterLambda(it)) }
    }

    /**
     * Adds a custom [ModelRanker] to the ranking pipeline.
     *
     * Rankers are applied lexicographically: each ranker resolves ties produced by the previous one.
     *
     * @param ranker Ranker to add.
     */
    public fun withRanker(ranker: ModelRanker): ModelSelectorBuilder = apply {
        rankers += ranker
    }

    /**
     * Adds a ranker defined by a lambda.
     *
     * @param rankerLambda Lambda that produces a [Ranking] from the input models.
     *   Must return exactly the models it receives.
     */
    public fun withRanker(rankerLambda: (List<LLModel>) -> Ranking): ModelSelectorBuilder = apply {
        rankers += ModelRanker(rankerLambda)
    }

    /**
     * Adds a filter that accepts only models supporting all [capabilities].
     *
     * @param capabilities Required model capabilities.
     */
    public fun withCapabilities(vararg capabilities: LLMCapability): ModelSelectorBuilder = apply {
        filters += ModelFilters.withCapabilities(*capabilities)
    }

    /**
     * Adds a filter that accepts only models with a context window of at least [minTokens].
     *
     * @param minTokens Minimum required context window size in tokens.
     */
    public fun withMinContextLength(minTokens: Long): ModelSelectorBuilder = apply {
        filters += ModelFilters.withMinContextLength(minTokens)
    }

    /**
     * Adds a ranker that prefers models with the largest context window.
     */
    public fun withBiggestContextLength(): ModelSelectorBuilder = apply {
        rankers += ModelRankers.biggestContextLength()
    }

    /**
     * Adds a ranker that prefers models with the highest maximum output token count.
     */
    public fun withMostOutputTokens(): ModelSelectorBuilder = apply {
        rankers += ModelRankers.mostOutputTokens()
    }

    /**
     * Sets the maximum number of models evaluated concurrently during the filter stage.
     *
     * Defaults to [DefaultModelSelector.DEFAULT_MAX_CONCURRENTLY_FILTERED_MODELS].
     *
     * @param max Positive concurrency limit.
     * @throws IllegalArgumentException If [max] is not positive.
     */
    public fun withMaxConcurrentlyFilteredModels(max: Int): ModelSelectorBuilder = apply {
        require(max > 0) { "max must be greater than 0." }
        maxConcurrentlyFilteredModels = max
    }

    /**
     * Builds and returns the configured [ModelSelector].
     */
    public fun build(): ModelSelector =
        DefaultModelSelector(
            filters = filters.toList(),
            rankers = rankers.toList(),
            maxConcurrentlyFilteredModels = maxConcurrentlyFilteredModels,
        )
}
