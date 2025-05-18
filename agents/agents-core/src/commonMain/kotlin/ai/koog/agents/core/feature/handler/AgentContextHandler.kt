package ai.koog.agents.core.feature.handler

import ai.koog.agents.core.agent.context.AIAgentContextBase


/**
 * Handler for creating a feature instance in a stage context.
 *
 * @param FeatureT The type of feature being handled
 */
public fun interface AgentContextHandler<FeatureT : Any> {
    /**
     * Creates a feature instance for the given stage context.
     *
     * @param context The stage context where the feature will be used
     * @return A new instance of the feature
     */
    public fun handle(context: AIAgentContextBase): FeatureT
}
