package ai.grazie.code.agents.core.feature.handler

import ai.grazie.code.agents.core.annotation.InternalAgentsApi
import ai.grazie.code.agents.core.agent.AIAgent
import ai.grazie.code.agents.core.agent.entity.AIAgentStrategy
import ai.grazie.code.agents.core.agent.entity.stage.AIAgentStage
import ai.grazie.code.agents.core.environment.AIAgentEnvironment

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
    var agentCreatedHandler: AgentCreatedHandler<FeatureT> =
        AgentCreatedHandler { context -> }

    /**
     * Configurable transformer used to manipulate or modify an instance of AgentEnvironment.
     * Allows customization of the environment during agent creation or updates by applying
     * the provided transformation logic.
     */
    var environmentTransformer: AgentEnvironmentTransformer<FeatureT> =
        AgentEnvironmentTransformer { _, it -> it }

    var agentStartedHandler: AgentStartedHandler =
        AgentStartedHandler { _ -> }

    var agentFinishedHandler: AgentFinishedHandler =
        AgentFinishedHandler { _, _ -> }

    var agentRunErrorHandler: AgentRunErrorHandler =
        AgentRunErrorHandler { _, _ -> }

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
    fun transformEnvironment(context: AgentCreateContext<FeatureT>, environment: AIAgentEnvironment) =
        environmentTransformer.transform(context, environment)

    /**
     * Transforms the provided AgentEnvironment using the configured environment transformer.
     *
     * @param environment The AgentEnvironment to be transformed
     */
    @Suppress("UNCHECKED_CAST")
    internal fun transformEnvironmentUnsafe(context: AgentCreateContext<*>, environment: AIAgentEnvironment) =
        transformEnvironment(context as AgentCreateContext<FeatureT>, environment)

    /**
     * Internal API for handling agent create events with type casting.
     *
     * @param context The context for updating the agent
     *
     * @suppress
     */
    @Suppress("UNCHECKED_CAST")
    @InternalAgentsApi
    suspend fun handleAgentCreatedUnsafe(context: AgentCreateContext<*>) =
        handleAgentCreated(context as AgentCreateContext<FeatureT>)
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
    fun transform(context: AgentCreateContext<FeatureT>, environment: AIAgentEnvironment): AIAgentEnvironment
}

fun interface AgentStartedHandler {
    suspend fun handle(strategyName: String)
}

fun interface AgentFinishedHandler {
    suspend fun handle(strategyName: String, result: String?)
}

fun interface AgentRunErrorHandler {
    suspend fun handle(strategyName: String, throwable: Throwable)
}

class AgentCreateContext<FeatureT>(
    val strategy: AIAgentStrategy,
    val agent: AIAgent,
    val feature: FeatureT
) {
    suspend fun readStages(block: suspend (List<AIAgentStage>) -> Unit) {
        block(strategy.stages)
    }
}

class StrategyUpdateContext<FeatureT>(
    val strategy: AIAgentStrategy,
    val feature: FeatureT
) {
    suspend fun readStages(block: suspend (List<AIAgentStage>) -> Unit) {
        block(strategy.stages)
    }
}


