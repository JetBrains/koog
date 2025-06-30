package ai.koog.agents.core.dsl.builder

import ai.koog.agents.core.agent.config.AIAgentConfigBase
import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.agent.context.AIAgentLLMContext
import ai.koog.agents.core.agent.entity.AIAgentStateManager
import ai.koog.agents.core.agent.entity.AIAgentStorage
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.AIAgentPipeline

/**
 * Context for merging parallel node execution results.
 *
 * This class provides DSL methods for selecting and folding results from parallel node executions.
 * It delegates all AIAgentContextBase methods and properties to the underlying context.
 *
 * @param Input The input type of the parallel nodes
 * @param Output The output type of the parallel nodes
 * @property underlyingContextBase The underlying context to delegate to
 * @property results The results of the parallel node executions
 */
@OptIn(InternalAgentsApi::class)
public class AIAgentParallelNodesMergeContext<Input, Output>(
    private val underlyingContextBase: AIAgentContextBase,
    public val results: List<ParallelResult<Input, Output>>
) : AIAgentContextBase {
    // Delegate all properties to the underlying context
    override val environment: AIAgentEnvironment get() = underlyingContextBase.environment
    override val agentInput: Any? get() = underlyingContextBase.agentInput
    override val config: AIAgentConfigBase get() = underlyingContextBase.config
    override val llm: AIAgentLLMContext get() = underlyingContextBase.llm
    override val stateManager: AIAgentStateManager get() = underlyingContextBase.stateManager
    override val storage: AIAgentStorage get() = underlyingContextBase.storage
    override val sessionId: String get() = underlyingContextBase.sessionId
    override val strategyId: String get() = underlyingContextBase.strategyId
    override val pipeline: AIAgentPipeline get() = underlyingContextBase.pipeline

    // Delegate all methods to the underlying context
    override fun <Feature : Any> feature(key: AIAgentStorageKey<Feature>): Feature? =
        underlyingContextBase.feature(key)

    override fun <Feature : Any> feature(feature: AIAgentFeature<*, Feature>): Feature? =
        underlyingContextBase.feature(feature)

    override fun <Feature : Any> featureOrThrow(feature: AIAgentFeature<*, Feature>): Feature =
        underlyingContextBase.featureOrThrow(feature)

    override fun copy(
        environment: AIAgentEnvironment,
        agentInput: Any?,
        config: AIAgentConfigBase,
        llm: AIAgentLLMContext,
        stateManager: AIAgentStateManager,
        storage: AIAgentStorage,
        sessionId: String,
        strategyId: String,
        pipeline: AIAgentPipeline
    ): AIAgentContextBase = underlyingContextBase.copy(
        environment = environment,
        agentInput = agentInput,
        config = config,
        llm = llm,
        stateManager = stateManager,
        storage = storage,
        sessionId = sessionId,
        strategyId = strategyId,
        pipeline = pipeline
    )

    override suspend fun fork(): AIAgentContextBase = underlyingContextBase.fork()

    override suspend fun replace(context: AIAgentContextBase): Unit = underlyingContextBase.replace(context)

    /**
     * Selects a result based on a predicate.
     *
     * @param predicate The predicate to use for selection
     * @return The NodeExecutionResult with the selected output and context
     * @throws NoSuchElementException if no result matches the predicate
     */
    public suspend fun selectBy(predicate: suspend (Output) -> Boolean): NodeExecutionResult<Output> {
        return results.first(predicate = { predicate(it.result.output) }).result
    }

    /**
     * Selects the maximum result based on a given comparison function and returns the corresponding
     * `NodeExecutionResult` containing the selected output and its associated context.
     *
     * @param function A lambda function to extract a comparable value from the `Output` object
     *                 for determining the maximum result.
     * @return The `NodeExecutionResult` containing the output and context of the result with the maximum
     *         value as determined by the comparison function.
     * @throws NoSuchElementException if the results list is empty.
     */
    public suspend fun <T : Comparable<T>> selectByMax(function: suspend (Output) -> T): NodeExecutionResult<Output> {
        return results.maxBy { function(it.result.output) }
            .let { NodeExecutionResult(it.result.output, it.result.context) }
    }

    /**
     * Selects a result from a list of outputs based on a provided selection function.
     *
     * @param selectIndex A lambda function that takes a list of outputs and returns the index of the desired output.
     * @return The NodeExecutionResult containing the output and context at the selected index.
     * @throws IndexOutOfBoundsException if the index returned by the selectIndex function is out of bounds.
     */
    public suspend fun selectByIndex(selectIndex: suspend (List<Output>) -> Int): NodeExecutionResult<Output> {
        val indexOfBest = selectIndex(results.map { it.result.output })
        return NodeExecutionResult(results[indexOfBest].result.output, results[indexOfBest].result.context)
    }

    /**
     * Folds the result output into a single value and leaves the base context.
     *
     * @param initial The initial value for the fold operation
     * @param operation The operation to apply to each result
     * @return The NodeExecutionResult with the folded output and the context from the first result
     * @throws NoSuchElementException if the results list is empty
     */
    public suspend fun <R> fold(
        initial: R,
        operation: suspend (acc: R, result: Output) -> R
    ): NodeExecutionResult<R> {
        val folded = results.map { it.result.output }.fold(initial) { r, t -> operation(r, t) }
        return NodeExecutionResult(folded, underlyingContextBase)
    }
}