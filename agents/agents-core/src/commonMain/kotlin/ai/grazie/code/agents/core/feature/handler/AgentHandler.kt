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
public class AgentHandler<FeatureT : Any>(public val feature: FeatureT) {

    /**
     * The handler for agent creation events.
     * Can be set outside the class.
     */
    public var agentCreatedHandler: AgentCreatedHandler<FeatureT> =
        AgentCreatedHandler { context -> }

    /**
     * Configurable transformer used to manipulate or modify an instance of AgentEnvironment.
     * Allows customization of the environment during agent creation or updates by applying
     * the provided transformation logic.
     */
    public var environmentTransformer: AgentEnvironmentTransformer<FeatureT> =
        AgentEnvironmentTransformer { _, it -> it }

    public var agentStartedHandler: AgentStartedHandler =
        AgentStartedHandler { _ -> }

    public var agentFinishedHandler: AgentFinishedHandler =
        AgentFinishedHandler { _, _ -> }

    public var agentRunErrorHandler: AgentRunErrorHandler =
        AgentRunErrorHandler { _, _ -> }

    /**
     * Handle agent creates events by delegating to the handler.
     *
     * @param context The context for updating the agent with the feature
     */
    public suspend fun handleAgentCreated(context: AgentCreateContext<FeatureT>) {
        agentCreatedHandler.handle(context)
    }

    /**
     * Transforms the provided AgentEnvironment using the configured environment transformer.
     *
     * @param environment The AgentEnvironment to be transformed
     */
    public fun transformEnvironment(context: AgentCreateContext<FeatureT>, environment: AIAgentEnvironment): AIAgentEnvironment =
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
    public suspend fun handleAgentCreatedUnsafe(context: AgentCreateContext<*>): Unit =
        handleAgentCreated(context as AgentCreateContext<FeatureT>)
}

/**
 * Handler for intercepting agent creation.
 *
 * @param FeatureT The type of feature being handled
 */
public fun interface AgentCreatedHandler<FeatureT : Any> {
    /**
     * Called when an agent is created.
     *
     * @param context The context for updating the agent with the feature
     */
    public suspend fun handle(context: AgentCreateContext<FeatureT>)
}

/**
 * Handler for transforming an instance of AgentEnvironment.
 *
 * Ex: useful for mocks in tests
 *
 * @param FeatureT The type of the feature associated with the agent.
 */
public fun interface AgentEnvironmentTransformer<FeatureT : Any> {
    /**
     * Transforms the provided agent environment based on the given context.
     *
     * @param context The context containing the agent, strategy, and feature information
     * @param environment The current agent environment to be transformed
     * @return The transformed agent environment
     */
    public fun transform(context: AgentCreateContext<FeatureT>, environment: AIAgentEnvironment): AIAgentEnvironment
}

public fun interface AgentStartedHandler {
    public suspend fun handle(strategyName: String)
}

public fun interface AgentFinishedHandler {
    public suspend fun handle(strategyName: String, result: String?)
}

public fun interface AgentRunErrorHandler {
    public suspend fun handle(strategyName: String, throwable: Throwable)
}

public class AgentCreateContext<FeatureT>(
    public val strategy: AIAgentStrategy,
    public val agent: AIAgent,
    public val feature: FeatureT
) {
    public suspend fun readStages(block: suspend (List<AIAgentStage>) -> Unit) {
        block(strategy.stages)
    }
}

public class StrategyUpdateContext<FeatureT>(
    public val strategy: AIAgentStrategy,
    public val feature: FeatureT
) {
    public suspend fun readStages(block: suspend (List<AIAgentStage>) -> Unit) {
        block(strategy.stages)
    }
}


