package ai.koog.agents.core.feature.handler

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.AIAgentEnvironment

/**
 * Feature implementation for agent and strategy interception.
 *
 * @param FeatureT The type of feature
 * @property feature The feature instance
 */
public class AgentHandler<FeatureT : Any>(public val feature: FeatureT) {

    /**
     * Configurable transformer used to manipulate or modify an instance of AgentEnvironment.
     * Allows customization of the environment during agent creation or updates by applying
     * the provided transformation logic.
     */
    public var environmentTransformer: AgentEnvironmentTransformer<FeatureT> =
        AgentEnvironmentTransformer { _, it -> it }

    public var beforeAgentStartedHandler: BeforeAgentStartedHandler<FeatureT> =
        BeforeAgentStartedHandler { context -> }

    public var agentFinishedHandler: AgentFinishedHandler =
        AgentFinishedHandler { _, _ -> }

    public var agentRunErrorHandler: AgentRunErrorHandler =
        AgentRunErrorHandler { _, _ -> }

    /**
     * Transforms the provided AgentEnvironment using the configured environment transformer.
     *
     * @param environment The AgentEnvironment to be transformed
     */
    public fun transformEnvironment(
        context: AgentCreateContext<FeatureT>,
        environment: AIAgentEnvironment
    ): AIAgentEnvironment =
        environmentTransformer.transform(context, environment)

    /**
     * Transforms the provided AgentEnvironment using the configured environment transformer.
     *
     * @param environment The AgentEnvironment to be transformed
     */
    @Suppress("UNCHECKED_CAST")
    internal fun transformEnvironmentUnsafe(context: AgentCreateContext<*>, environment: AIAgentEnvironment) =
        transformEnvironment(context as AgentCreateContext<FeatureT>, environment)

    public suspend fun handleBeforeAgentStarted(context: AgentStartContext<FeatureT>) {
        beforeAgentStartedHandler.handle(context)
    }

    @Suppress("UNCHECKED_CAST")
    @InternalAgentsApi
    public suspend fun handleBeforeAgentStartedUnsafe(context: AgentStartContext<*>) {
        handleBeforeAgentStarted(context as AgentStartContext<FeatureT>)
    }
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

public fun interface BeforeAgentStartedHandler<TFeature: Any> {
    public suspend fun handle(context: AgentStartContext<TFeature>)
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
    public suspend fun readStrategy(block: suspend (AIAgentStrategy) -> Unit) {
        block(strategy)
    }
}

public class AgentStartContext<TFeature>(
    public val strategy: AIAgentStrategy,
    public val agent: AIAgent,
    public val feature: TFeature
) {
    public suspend fun readStrategy(block: suspend (AIAgentStrategy) -> Unit) {
        block(strategy)
    }
}

public class StrategyUpdateContext<FeatureT>(
    public val strategy: AIAgentStrategy,
    public val feature: FeatureT
) {
    public suspend fun readStrategy(block: suspend (AIAgentStrategy) -> Unit) {
        block(strategy)
    }
}


