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
import ai.koog.agents.core.tools.ToolDescriptor
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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
@OptIn(ExperimentalUuidApi::class, InternalAgentsApi::class)
public class AIAgentParallelNodesMergeContext<Input, Output>(
    private val underlyingContextBase: AIAgentContextBase,
    public val results: List<ParallelResult<Input, Output>>
) : AIAgentContextBase {
    // Delegate all properties to the underlying context
    override val environment: AIAgentEnvironment get() = underlyingContextBase.environment
    override val agentInput: String get() = underlyingContextBase.agentInput
    override val config: AIAgentConfigBase get() = underlyingContextBase.config
    override val llm: AIAgentLLMContext get() = underlyingContextBase.llm
    override val stateManager: AIAgentStateManager get() = underlyingContextBase.stateManager
    override val storage: AIAgentStorage get() = underlyingContextBase.storage
    override val sessionUuid: Uuid get() = underlyingContextBase.sessionUuid
    override val strategyId: String get() = underlyingContextBase.strategyId
    override val pipeline: AIAgentPipeline get() = underlyingContextBase.pipeline

    // Delegate all methods to the underlying context
    override fun <Feature : Any> feature(key: AIAgentStorageKey<Feature>): Feature? =
        underlyingContextBase.feature(key)

    override fun <Feature : Any> feature(feature: AIAgentFeature<*, Feature>): Feature? =
        underlyingContextBase.feature(feature)

    override fun <Feature : Any> featureOrThrow(feature: AIAgentFeature<*, Feature>): Feature =
        underlyingContextBase.featureOrThrow(feature)

    override fun copyWithTools(tools: List<ToolDescriptor>): AIAgentContextBase =
        underlyingContextBase.copyWithTools(tools)

    override fun copy(
        environment: AIAgentEnvironment?,
        agentInput: String?,
        config: AIAgentConfigBase?,
        llm: AIAgentLLMContext?,
        stateManager: AIAgentStateManager?,
        storage: AIAgentStorage?,
        sessionUuid: Uuid?,
        strategyId: String?,
        pipeline: AIAgentPipeline?
    ): AIAgentContextBase = underlyingContextBase.copy(
        environment, agentInput, config, llm, stateManager,
        storage, sessionUuid, strategyId, pipeline
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
    public fun selectBy(predicate: (Output) -> Boolean): NodeExecutionResult<Output> {
        return results.first(predicate = { predicate(it.result.output) }).result
    }

    /**
     * Folds the result output into a single value and leaves the base context.
     *
     * @param initial The initial value for the fold operation
     * @param operation The operation to apply to each result
     * @return The NodeExecutionResult with the folded output and the context from the first result
     * @throws NoSuchElementException if the results list is empty
     */
    public fun <R> fold(
        initial: R,
        operation: (acc: R, result: Output) -> R
    ): NodeExecutionResult<R> {
        val folded = results.map { it.result.output }.fold(initial, operation)
        return NodeExecutionResult(folded, underlyingContextBase)
    }
}