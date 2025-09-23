package ai.koog.agents.core.feature

import ai.koog.agents.core.feature.config.FeatureConfig
import kotlinx.datetime.Clock

/**
 * Represents a specific implementation of an AI agent pipeline
 * that does not use graph-based processing. This class inherits
 * from the base AIAgentPipeline class and may be used for handling
 * workflows or data processing tasks that do not require graph-based
 * data structures.
 *
 * @param clock The clock used for time-based operations within the pipeline
 */
public class AIAgentNonGraphPipeline(clock: Clock = Clock.System) : AIAgentPipeline(clock) {

    /**
     * Installs a non-graph feature into the pipeline with the provided configuration.
     *
     * @param Config The type of the feature configuration
     * @param Feature The type of the feature being installed
     * @param feature The feature implementation to be installed
     * @param configure A lambda to customize the feature configuration
     */
    public fun <Config : FeatureConfig, Feature : Any> install(
        feature: AIAgentNonGraphFeature<Config, Feature>,
        configure: Config.() -> Unit
    ) {
        val config = feature.createInitialConfig().apply { configure() }
        feature.install(
            config = config,
            pipeline = this,
        )

        registeredFeatures[feature.key] = config
    }
}
