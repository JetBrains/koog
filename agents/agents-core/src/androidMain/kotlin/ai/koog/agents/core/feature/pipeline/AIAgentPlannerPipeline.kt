@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "MissingKDocForPublicAPI")

package ai.koog.agents.core.feature.pipeline

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.feature.AIAgentPlannerFeature
import ai.koog.agents.core.feature.config.FeatureConfig
import kotlin.time.Clock

public actual open class AIAgentPlannerPipeline actual constructor(
    agentConfig: AIAgentConfig,
    clock: Clock,
    private val basePipelineDelegate: AIAgentPipelineImpl
) : AIAgentPipeline(agentConfig, clock), AIAgentPlannerPipelineAPI by AIAgentPlannerPipelineImpl(agentConfig, clock, basePipelineDelegate) {

    /**
     * Installs a non-graph feature into the pipeline with the provided configuration.
     *
     * @param TConfig The type of the feature configuration
     * @param TFeature The type of the feature being installed
     * @param feature The feature implementation to be installed
     * @param configure A lambda to customize the feature configuration
     */
    public actual fun <TConfig : FeatureConfig, TFeature : Any> install(
        feature: AIAgentPlannerFeature<TConfig, TFeature>,
        configure: TConfig.() -> Unit,
    ) {
        val featureConfig = feature.createInitialConfig().apply { configure() }
        val featureImpl = feature.install(
            config = featureConfig,
            pipeline = this,
        )

        basePipelineDelegate.install(feature.key, featureConfig, featureImpl)
    }
}
