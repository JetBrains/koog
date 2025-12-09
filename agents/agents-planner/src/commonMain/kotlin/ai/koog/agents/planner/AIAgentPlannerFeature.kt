package ai.koog.agents.planner

import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.config.FeatureConfig

/**
 * Represents a planner-specific AI agent feature that can be installed into an [AIAgentPlannerPipeline].
 *
 * @param TConfig The type of configuration required for the feature, extending [FeatureConfig].
 * @param TFeatureImpl The type representing the concrete implementation of the feature.
 */
public interface AIAgentPlannerFeature<TConfig : FeatureConfig, TFeatureImpl : Any> : AIAgentFeature<TConfig, TFeatureImpl> {
    /**
     * Installs the feature into the specified [pipeline].
     * @return The implementation of the feature.
     */
    public fun install(config: TConfig, pipeline: AIAgentPlannerPipeline): TFeatureImpl
}
