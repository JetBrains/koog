package ai.grazie.code.agents.core.feature

import ai.grazie.code.agents.core.annotation.InternalAgentsApi
import ai.grazie.code.agents.core.agent.entity.AgentStorageKey
import ai.grazie.code.agents.core.feature.config.FeatureConfig

/**
 * A class for Agent Feature that can be added to an agent pipeline,
 * The feature stands for providing specific functionality and configuration capabilities.
 *
 * @param Config The type representing the configuration for this feature.
 * @param FeatureT The type of the feature implementation.
 */
interface AgentFeature<Config : FeatureConfig, FeatureT : Any> {

    /**
     * A key used to uniquely identify a feature of type [FeatureT] within the agent storage.
     */
    val key: AgentStorageKey<FeatureT>

    /**
     * Creates and returns an initial configuration for the feature.
     */
    fun createInitialConfig(): Config

    /**
     * Installs the feature into the specified [AgentPipeline].
     */
    fun install(config: Config, pipeline: AgentPipeline)

    /**
     * Installs the feature into the specified [AgentPipeline] using an unsafe configuration type cast.
     */
    @Suppress("UNCHECKED_CAST")
    @InternalAgentsApi
    fun installUnsafe(config: Any?, pipeline: AgentPipeline) = install(config as Config, pipeline)
}
