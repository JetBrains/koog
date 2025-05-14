package ai.grazie.code.agents.core.feature

import ai.grazie.code.agents.core.annotation.InternalAgentsApi
import ai.grazie.code.agents.core.agent.entity.AIAgentStorageKey
import ai.grazie.code.agents.features.common.config.FeatureConfig

/**
 * A class for Agent Feature that can be added to an agent pipeline,
 * The feature stands for providing specific functionality and configuration capabilities.
 *
 * @param Config The type representing the configuration for this feature.
 * @param FeatureT The type of the feature implementation.
 */
interface AIAgentFeatureBase<Config : FeatureConfig, FeatureT : Any> {

    /**
     * A key used to uniquely identify a feature of type [FeatureT] within the local agent storage.
     */
    val key: AIAgentStorageKey<FeatureT>

    /**
     * Creates and returns an initial configuration for the feature.
     */
    fun createInitialConfig(): Config

    /**
     * Installs the feature into the specified [AIAgentPipeline].
     */
    fun install(config: Config, pipeline: AIAgentPipeline)

    /**
     * Installs the feature into the specified [AIAgentPipeline] using an unsafe configuration type cast.
     *
     * @suppress
     */
    @Suppress("UNCHECKED_CAST")
    @InternalAgentsApi
    fun installUnsafe(config: Any?, pipeline: AIAgentPipeline) = install(config as Config, pipeline)
}
