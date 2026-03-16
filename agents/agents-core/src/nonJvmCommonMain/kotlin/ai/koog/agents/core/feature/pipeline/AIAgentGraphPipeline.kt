@file:Suppress("MissingKDocForPublicAPI", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.feature.pipeline

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.config.FeatureConfig
import kotlin.time.Clock

public actual open class AIAgentGraphPipeline actual constructor(
    config: AIAgentConfig,
    clock: Clock
) : AIAgentGraphPipelineCommon(config, clock) {

    public actual fun <TConfig : FeatureConfig, TFeatureImpl : Any> install(
        feature: AIAgentGraphFeature<TConfig, TFeatureImpl>,
        configure: TConfig.() -> Unit,
    ) {
        val featureConfig = feature.createInitialConfig(config).apply { configure() }
        val featureImpl = feature.install(
            config = featureConfig,
            pipeline = this,
        )

        install(feature.key, featureConfig, featureImpl)
    }
}
