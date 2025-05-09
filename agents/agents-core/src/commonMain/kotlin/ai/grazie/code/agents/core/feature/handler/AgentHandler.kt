package ai.grazie.code.agents.core.feature.handler

import ai.grazie.code.agents.core.annotation.InternalAgentsApi
import ai.grazie.code.agents.core.agent.AIAgentBase
import ai.grazie.code.agents.core.agent.entity.LocalAgentStrategy
import ai.grazie.code.agents.core.agent.entity.stage.LocalAgentStage
import ai.grazie.code.agents.core.environment.AgentEnvironment

/**
 * Feature implementation for agent and strategy interception.
 *
 * @param FeatureT The type of feature
 * @property feature The feature instance
 */
class AgentHandler<FeatureT : Any>(val feature: FeatureT) {

    /**
     * The handler for agent creation events.
     * Can be set outside the class.
     */
    var agentCreatedHandler: AgentCreatedHandler<FeatureT> = AgentCreatedHandler { context -> }

    /**
     * Handler invoked when a strategy is started. This can be used to perform custom logic
     * related to strategy initiation for a specific feature.
     */
    var strategyStartedHandler: StrategyStartedHandler<FeatureT> = StrategyStartedHandler { context -> }

    /**
     * Configurable transformer used to manipulate or modify an instance of AgentEnvironment.
     * Allows customization of the environment during agent creation or updates by applying
     * the provided transformation logic.
     */
    var environmentTransformer: AgentEnvironmentTransformer<FeatureT> = AgentEnvironmentTransformer { _, it -> it }


    /**
     * Handle agent creates events by delegating to the handler.
     *
     * @param context The context for updating the agent with the feature
     */
    suspend fun handleAgentCreated(context: AgentCreateContext<FeatureT>) {
        agentCreatedHandler.handle(context)
    }

    /**
     * Transforms the provided AgentEnvironment using the configured environment transformer.
     *
     * @param environment The AgentEnvironment to be transformed
     */
    fun transformEnvironment(context: AgentCreateContext<FeatureT>, environment: AgentEnvironment) =
        environmentTransformer.transform(context, environment)

    /**
     * Transforms the provided AgentEnvironment using the configured environment transformer.
     *
     * @param environment The AgentEnvironment to be transformed
     */
    @Suppress("UNCHECKED_CAST")
    internal fun transformEnvironmentUnsafe(context: AgentCreateContext<*>, environment: AgentEnvironment) =
        transformEnvironment(context as AgentCreateContext<FeatureT>, environment)

    /**
     * Internal API for handling agent create events with type casting.
     *
     * @param context The context for updating the agent
     */
    @Suppress("UNCHECKED_CAST")
    @InternalAgentsApi
    suspend fun handleAgentCreatedUnsafe(context: AgentCreateContext<*>) =
        handleAgentCreated(context as AgentCreateContext<FeatureT>)

    /**
     * Handles strategy starts events by delegating to the handler.
     *
     * @param context The context for updating the agent with the feature
     */
    suspend fun handleStrategyStarted(context: StrategyUpdateContext<FeatureT>) {
        strategyStartedHandler.handle(context)
    }

    /**
     * Internal API for handling strategy start events with type casting.
     *
     * @param context The context for updating the agent
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun handleStrategyStartedUnsafe(context: StrategyUpdateContext<*>) {
        handleStrategyStarted(context as StrategyUpdateContext<FeatureT>)
    }
}

/**
 * Handler for intercepting agent creation.
 *
 * @param FeatureT The type of feature being handled
 */
fun interface AgentCreatedHandler<FeatureT : Any> {
    /**
     * Called when an agent is created.
     *
     * @param context The context for updating the agent with the feature
     */
    suspend fun handle(context: AgentCreateContext<FeatureT>)
}

/**
 * Handler for transforming an instance of AgentEnvironment.
 *
 * Ex: useful for mocks in tests
 *
 * @param FeatureT The type of the feature associated with the agent.
 */
fun interface AgentEnvironmentTransformer<FeatureT : Any> {
    /**
     * Transforms the provided agent environment based on the given context.
     *
     * @param context The context containing the agent, strategy, and feature information
     * @param environment The current agent environment to be transformed
     * @return The transformed agent environment
     */
    fun transform(context: AgentCreateContext<FeatureT>, environment: AgentEnvironment): AgentEnvironment
}

fun interface StrategyStartedHandler<FeatureT : Any> {
    suspend fun handle(context: StrategyUpdateContext<FeatureT>)
}

class AgentCreateContext<FeatureT>(
    val strategy: LocalAgentStrategy,
    val agent: AIAgentBase,
    val feature: FeatureT
) {
    suspend fun readStages(block: suspend (List<LocalAgentStage>) -> Unit) {
        block(strategy.stages)
    }
}

class StrategyUpdateContext<FeatureT>(
    val strategy: LocalAgentStrategy,
    val feature: FeatureT
) {
    suspend fun readStages(block: suspend (List<LocalAgentStage>) -> Unit) {
        block(strategy.stages)
    }
}
