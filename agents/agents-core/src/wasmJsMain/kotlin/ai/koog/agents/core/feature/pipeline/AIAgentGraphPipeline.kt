@file:Suppress("MissingKDocForPublicAPI", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.core.feature.pipeline

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.config.FeatureConfig
import kotlinx.datetime.Clock

public actual open class AIAgentGraphPipeline actual constructor(
    agentConfig: AIAgentConfig,
    clock: Clock,
    private val basePipelineDelegate: AIAgentPipelineImpl
) : AIAgentPipeline(agentConfig, clock),
    AIAgentGraphPipelineAPI by AIAgentGraphPipelineImpl(agentConfig, clock, basePipelineDelegate) {

    public actual fun <TConfig : FeatureConfig, TFeatureImpl : Any> install(
        feature: AIAgentGraphFeature<TConfig, TFeatureImpl>,
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
