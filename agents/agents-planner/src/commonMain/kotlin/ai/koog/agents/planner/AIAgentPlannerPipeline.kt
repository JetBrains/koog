package ai.koog.agents.planner

import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.pipeline.AIAgentPipeline
import kotlinx.datetime.Clock

/**
 * Represents a specific implementation of an AI agent pipeline that uses a planner approach.
 *
 * @property clock The clock used for time-based operations within the pipeline
 */
public class AIAgentPlannerPipeline(clock: Clock = Clock.System) : AIAgentPipeline(clock) {

    /**
     * Installs a non-graph feature into the pipeline with the provided configuration.
     *
     * @param TConfig The type of the feature configuration
     * @param TFeature The type of the feature being installed
     * @param feature The feature implementation to be installed
     * @param configure A lambda to customize the feature configuration
     */
    public fun <TConfig : FeatureConfig, TFeature : Any> install(
        feature: AIAgentPlannerFeature<TConfig, TFeature>,
        configure: TConfig.() -> Unit,
    ) {
        val featureConfig = feature.createInitialConfig().apply { configure() }
        val featureImpl = feature.install(
            config = featureConfig,
            pipeline = this,
        )

        super.install(feature.key, featureConfig, featureImpl)
    }
}
