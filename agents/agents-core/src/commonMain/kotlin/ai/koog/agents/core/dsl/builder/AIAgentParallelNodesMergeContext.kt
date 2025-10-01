package ai.koog.agents.core.dsl.builder

import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.context.AIAgentLLMContext
import ai.koog.agents.core.agent.entity.AIAgentStateManager
import ai.koog.agents.core.agent.entity.AIAgentStorage
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.AIAgentGraphPipeline
import ai.koog.prompt.message.Message
import kotlin.reflect.KType

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
    private val underlyingContextBase: AIAgentGraphContextBase,
    public val results: List<ParallelResult<Input, Output>>,
) : AIAgentGraphContextBase {
    override val parentContext: AIAgentGraphContextBase = underlyingContextBase

    // Delegate all properties to the underlying context
    override val environment: AIAgentEnvironment get() = underlyingContextBase.environment
    override val agent: GraphAIAgent<*, *> get() = underlyingContextBase.agent
    override val agentInput: Any? get() = underlyingContextBase.agentInput
    override val agentInputType: KType get() = underlyingContextBase.agentInputType

    override val config: AIAgentConfig get() = underlyingContextBase.config
    override val llm: AIAgentLLMContext get() = underlyingContextBase.llm
    override val stateManager: AIAgentStateManager get() = underlyingContextBase.stateManager
    override val storage: AIAgentStorage get() = underlyingContextBase.storage
    override val runId: String get() = underlyingContextBase.runId
    override val strategyName: String get() = underlyingContextBase.strategyName
    override val pipeline: AIAgentGraphPipeline get() = underlyingContextBase.pipeline

    override fun store(key: AIAgentStorageKey<*>, value: Any) {
        underlyingContextBase.store(key, value)
    }

    override fun <T> get(key: AIAgentStorageKey<*>): T? {
        return underlyingContextBase.get(key)
    }

    override fun remove(key: AIAgentStorageKey<*>): Boolean {
        return underlyingContextBase.remove(key)
    }

    // Delegate all methods to the underlying context
    override fun <Feature : Any> feature(key: AIAgentStorageKey<Feature>): Feature? =
        underlyingContextBase.feature(key)

    override fun <Feature : Any> feature(feature: AIAgentFeature<*, Feature>): Feature? =
        underlyingContextBase.feature(feature)

    override fun <Feature : Any> featureOrThrow(feature: AIAgentFeature<*, Feature>): Feature =
        underlyingContextBase.featureOrThrow(feature)

    override suspend fun getHistory(): List<Message> = underlyingContextBase.getHistory()

    /**
     * Creates a copy of the current AIAgentContextBase object with the specified parameters.
     */
    override fun copy(
        environment: AIAgentEnvironment,
        agentInput: Any?,
        agentInputType: KType,
        config: AIAgentConfig,
        llm: AIAgentLLMContext,
        stateManager: AIAgentStateManager,
        storage: AIAgentStorage,
        runId: String,
        strategyName: String,
        pipeline: AIAgentGraphPipeline
    ): AIAgentGraphContextBase = underlyingContextBase.copy(
        environment = environment,
        agentInput = agentInput,
        agentInputType = agentInputType,
        config = config,
        llm = llm,
        stateManager = stateManager,
        storage = storage,
        runId = runId,
        strategyName = strategyName,
        pipeline = pipeline
    )

    /**
     * Creates a forked instance of the underlying agent context, resulting in a new independent
     * copy of the `AIAgentContextBase`. This can be used to create isolated contexts for
     * parallel or independent operations.
     *
     * @return A new instance of `AIAgentContextBase` that is a fork of the current context.
     */
    override suspend fun fork(): AIAgentGraphContextBase = underlyingContextBase.fork()

    /**
     * Replaces the current context with the specified context in the underlying context base.
     *
     * @param context The new context to replace the current one in the underlying context base.
     * @return Unit
     */
    override suspend fun replace(context: AIAgentContext): Unit = underlyingContextBase.replace(context)

    /**
     * Selects a result based on a predicate.
     *
     * @param predicate The predicate to use for selection
     * @return The NodeExecutionResult with the selected output and context
     * @throws NoSuchElementException if no result matches the predicate
     */
    public suspend fun selectBy(predicate: suspend (Output) -> Boolean): ParallelNodeExecutionResult<Output> {
        return results.first(predicate = { predicate(it.nodeResult.output) }).nodeResult
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
    public suspend fun <T : Comparable<T>> selectByMax(
        function: suspend (Output) -> T
    ): ParallelNodeExecutionResult<Output> {
        return results.maxBy { function(it.nodeResult.output) }
            .let { ParallelNodeExecutionResult(it.nodeResult.output, it.nodeResult.context) }
    }

    /**
     * Selects a result from a list of outputs based on a provided selection function.
     *
     * @param selectIndex A lambda function that takes a list of outputs and returns the index of the desired output.
     * @return The NodeExecutionResult containing the output and context at the selected index.
     * @throws IndexOutOfBoundsException if the index returned by the selectIndex function is out of bounds.
     */
    public suspend fun selectByIndex(selectIndex: suspend (List<Output>) -> Int): ParallelNodeExecutionResult<Output> {
        val indexOfBest = selectIndex(results.map { it.nodeResult.output })
        return ParallelNodeExecutionResult(
            results[indexOfBest].nodeResult.output,
            results[indexOfBest].nodeResult.context
        )
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
    ): ParallelNodeExecutionResult<R> {
        val folded = results.map { it.nodeResult.output }.fold(initial) { r, t -> operation(r, t) }
        return ParallelNodeExecutionResult(folded, underlyingContextBase)
    }
}
