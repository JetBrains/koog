package ai.grazie.code.agents.core.feature.handler

import ai.grazie.code.agents.core.agent.entity.stage.LocalAgentStageContext

/**
 * Handler for creating a feature instance in a stage context.
 *
 * @param FeatureT The type of feature being handled
 */
fun interface StageContextHandler<FeatureT : Any> {
    /**
     * Creates a feature instance for the given stage context.
     *
     * @param context The stage context where the feature will be used
     * @return A new instance of the feature
     */
    fun handle(context: LocalAgentStageContext): FeatureT
}
