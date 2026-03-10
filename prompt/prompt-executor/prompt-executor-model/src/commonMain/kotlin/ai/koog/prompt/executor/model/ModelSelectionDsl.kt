package ai.koog.prompt.executor.model

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.Flow

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
public suspend fun SelectingPromptExecutor.execute(
    prompt: Prompt,
    tools: List<ToolDescriptor> = emptyList(),
    selection: ModelSelectionBuilder.() -> Unit,
): List<Message.Response> = execute(
    prompt = prompt,
    modelSelector = ModelSelectionBuilder().apply(selection).build(),
    tools = tools,
)

public fun SelectingPromptExecutor.executeStreaming(
    prompt: Prompt,
    tools: List<ToolDescriptor> = emptyList(),
    selection: ModelSelectionBuilder.() -> Unit,
): Flow<StreamFrame> = executeStreaming(
    prompt = prompt,
    modelSelector = ModelSelectionBuilder().apply(selection).build(),
    tools = tools,
)

public suspend fun SelectingPromptExecutor.executeMultipleChoices(
    prompt: Prompt,
    tools: List<ToolDescriptor> = emptyList(),
    selection: ModelSelectionBuilder.() -> Unit,
): List<LLMChoice> = executeMultipleChoices(
    prompt = prompt,
    modelSelector = ModelSelectionBuilder().apply(selection).build(),
    tools = tools,
)

public suspend fun SelectingPromptExecutor.moderateWithSelection(
    prompt: Prompt,
    selection: ModelSelectionBuilder.() -> Unit,
): ModerationResult = moderate(
    prompt = prompt,
    modelSelector = ModelSelectionBuilder().apply(selection).build()
)

@ModelSelectionDsl
public class ModelSelectionBuilder {
    private val filters: MutableList<ModelFilter> = mutableListOf()
    private val rankers: MutableList<ModelRanker> = mutableListOf()
    private var maxConcurrentlyFilteredModels: Int = DefaultModelSelector.DEFAULT_MAX_CONCURRENTLY_FILTERED_MODELS

    public fun withFilter(filter: ModelFilter): ModelSelectionBuilder = apply {
        filters += filter
    }

    public fun withRanker(ranker: ModelRanker): ModelSelectionBuilder = apply {
        rankers += ranker
    }

    public fun withCapabilities(vararg capabilities: LLMCapability): ModelSelectionBuilder = apply {
        filters += ModelFilters.withCapabilities(*capabilities)
    }

    public fun withMinContextLength(minTokens: Long): ModelSelectionBuilder = apply {
        filters += ModelFilters.withMinContextLength(minTokens)
    }

    public fun withBiggestContextLength(): ModelSelectionBuilder = apply {
        rankers += ModelRankers.biggestContextLength()
    }

    public fun withMostOutputTokens(): ModelSelectionBuilder = apply {
        rankers += ModelRankers.mostOutputTokens()
    }

    public fun withMaxConcurrentlyFilteredModels(max: Int): ModelSelectionBuilder = apply {
        require(max > 0) { "max must be greater than 0." }
        maxConcurrentlyFilteredModels = max
    }

    public fun build(): ModelSelector =
        DefaultModelSelector(
            filters = filters.toList(),
            rankers = rankers.toList(),
            maxConcurrentlyFilteredModels = maxConcurrentlyFilteredModels,
        )
}
