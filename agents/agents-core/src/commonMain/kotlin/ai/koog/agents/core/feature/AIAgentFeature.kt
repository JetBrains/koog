package ai.koog.agents.core.feature

import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.feature.config.FeatureConfig

/**
 * A class for Agent Feature that can be added to an agent pipeline,
 * The feature stands for providing specific functionality and configuration capabilities.
 *
 * @param Config The type representing the configuration for this feature.
 * @param TFeature The type of the feature implementation.
 */
public interface AIAgentFeature<Config : FeatureConfig, TFeature : Any> {

    /**
     * A key used to uniquely identify a feature of type [TFeature] within the local agent storage.
     */
    public val key: AIAgentStorageKey<TFeature>

    /**
     * Creates and returns an initial configuration for the feature.
     */
    public fun createInitialConfig(): Config
}

/**
 * Represents a graph-specific AI agent feature that can be installed into an instance of [AIAgentGraphPipeline].
 *
 * This interface extends the functionality provided by the [AIAgentFeature] interface to accommodate
 * the unique requirements of graph-based agent pipelines.
 *
 * @param Config The type of configuration required for the feature, extending [FeatureConfig].
 * @param TFeature The type representing the concrete implementation of the feature.
 */
public interface AIAgentGraphFeature<Config : FeatureConfig, TFeature : Any> : AIAgentFeature<Config, TFeature> {
    /**
     * Installs the feature into the specified [AIAgentPipeline].
     */
    public fun install(config: Config, pipeline: AIAgentGraphPipeline)

    /**
     * Installs the feature into the specified [AIAgentPipeline] using an unsafe configuration type cast.
     *
     * @suppress
     */
    @Suppress("UNCHECKED_CAST")
    @InternalAgentsApi
    public fun installUnsafe(config: Any?, pipeline: AIAgentGraphPipeline): Unit = install(config as Config, pipeline)
}

/**
 * Represents a non-graph-specific AI agent feature that can be installed into an instance of [AIAgentNonGraphPipeline].
 *
 * This interface extends [AIAgentFeature] for non-graph pipelines where node-based handlers are not required.
 *
 * @param Config The type of configuration required for the feature, extending [FeatureConfig].
 * @param TFeature The type representing the concrete implementation of the feature.
 */
public interface AIAgentNonGraphFeature<Config : FeatureConfig, TFeature : Any> : AIAgentFeature<Config, TFeature> {
    /**
     * Installs the feature into the specified [AIAgentNonGraphPipeline].
     */
    public fun install(config: Config, pipeline: AIAgentNonGraphPipeline)

    /**
     * Installs the feature into the specified [AIAgentNonGraphPipeline] using an unsafe configuration type cast.
     *
     * @suppress
     */
    @Suppress("UNCHECKED_CAST")
    @InternalAgentsApi
    public fun installUnsafe(config: Any?, pipeline: AIAgentNonGraphPipeline): Unit = install(config as Config, pipeline)
}
