package ai.koog.agents.core.agent.session

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.pipeline.AIAgentPipeline
import kotlin.reflect.KClass

/**
 * Represents a session for running an [ai.koog.agents.core.agent.AIAgent].
 *
 * An [AIAgentRunSession] encapsulates a single execution lifecycle of an AI agent, providing
 * a controlled environment for running the agent's strategy with specific input and context.
 * Each session is independent and manages its own execution state, pipeline coordination,
 * and resource lifecycle.
 *
 * Sessions are typically created through [ai.koog.agents.core.agent.AIAgentBase.createSession]
 * and can be reused for multiple sequential executions. The session handles:
 * - Pipeline preparation and feature initialization
 * - Strategy execution with proper error handling
 * - Event notifications at each execution stage
 * - Resource cleanup after execution completes
 *
 * @param Input the type of input data required by the agent's strategy.
 * @param Output the type of output data produced by the agent's strategy.
 * @param TContext the type of context used during execution, extending [AIAgentContext].
 *
 * @see ai.koog.agents.core.agent.AIAgentBase.createSession
 * @see ai.koog.agents.core.agent.AIAgent
 */
public interface AIAgentRunSession<Input, Output, TContext : AIAgentContext> {
    /**
     * Executes the agent pipeline with the given context and input, producing an output.
     *
     * @param input The input provided to the pipeline during execution.
     * @return The output produced by the pipeline execution.
     */
    public suspend fun run(
        input: Input
    ): Output

    /**
     * Returns the pipeline associated with this session.
     * The pipeline becomes available after the first call to [run] with a pipeline parameter.
     *
     * @return The AI agent pipeline used by this session, or null if no pipeline has been set yet.
     */
    public fun pipeline(): AIAgentPipeline?

    /**
     * Returns the context for the current AI agent execution session.
     * The context provides access to essential details such as environment, input, execution state,
     * configuration, and other metadata required for the AI agent's operations.
     *
     * @return The [AIAgentContext] instance associated with the current execution session.
     */
    public fun context(): TContext
}

/**
 * Retrieves a feature from the [AIAgentRunSession.pipeline] associated with this session using the specified key.
 *
 * @param TFeature A feature implementation type.
 * @param feature A feature to fetch.
 * @param featureClass The [KClass] of the feature to be retrieved.
 * @return The feature associated with the provided key, or null if no matching feature is found.
 * @throws IllegalArgumentException if the specified [featureClass] does not correspond to a registered feature.
 */
public fun <Input, Output, TContext : AIAgentContext, TFeature : Any> AIAgentRunSession<Input, Output, TContext>.feature(
    featureClass: KClass<TFeature>,
    feature: AIAgentFeature<*, TFeature>
): TFeature? = pipeline()?.feature(featureClass, feature)

/**
 * Retrieves a feature from the [AIAgentRunSession.pipeline] associated with this session using the specified key.
 *
 * @param feature A feature to fetch.
 * @return The feature associated with the provided key, or null if no matching feature is found.
 * @throws IllegalArgumentException if the specified [feature] does not correspond to a registered feature.
 */
public inline fun <Input, Output, TContext : AIAgentContext, reified TFeature : Any> AIAgentRunSession<Input, Output, TContext>.feature(
    feature: AIAgentFeature<*, TFeature>
): TFeature? = feature(TFeature::class, feature)

/**
 * Retrieves a feature from the [AIAgentRunSession.pipeline] associated with this session using the specified key
 * or throws an exception if it is not available.
 *
 * @param feature A feature to fetch.
 * @return The feature associated with the provided key
 * @throws IllegalStateException if the [TFeature] feature does not correspond to a registered feature.
 * @throws NoSuchElementException if the feature is not found.
 */
public inline fun <Input, Output, TContext : AIAgentContext, reified TFeature : Any> AIAgentRunSession<Input, Output, TContext>.featureOrThrow(
    feature: AIAgentFeature<*, TFeature>
): TFeature = feature(feature) ?: throw NoSuchElementException("Feature ${feature.key} is not found.")
