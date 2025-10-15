package ai.koog.agents.core.feature

import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.pipeline.AIAgentFunctionalPipeline
import ai.koog.agents.core.feature.pipeline.AIAgentGraphPipeline

/**
 * A class for Agent Feature that can be added to an agent pipeline,
 * The feature stands for providing specific functionality and configuration capabilities.
 *
 * @param TConfig The type representing the configuration for this feature.
 * @param TFeatureImpl The type of the feature implementation.
 */
public interface AIAgentFeature<TConfig : FeatureConfig, TFeatureImpl : Any> {

    /**
     * A key used to uniquely identify a feature of type [TFeatureImpl] within the local agent storage.
     */
    public val key: AIAgentStorageKey<TFeatureImpl>

    /**
     * Creates and returns an initial configuration for the feature.
     */
    public fun createInitialConfig(): TConfig
}

/**
 * Represents a graph-specific AI agent feature that can be installed into an [AIAgentGraphPipeline].
 *
 * @param TConfig The type of configuration required for the feature, extending [FeatureConfig].
 * @param TFeatureImpl The type representing the concrete implementation of the feature.
 */
public interface AIAgentGraphFeature<TConfig : FeatureConfig, TFeatureImpl : Any> : AIAgentFeature<TConfig, TFeatureImpl> {
    /**
     * Installs the feature into the specified [pipeline].
     * @return The implementation of the feature.
     */
    public fun install(config: TConfig, pipeline: AIAgentGraphPipeline): TFeatureImpl
}

/**
 * Represents a functional-specific AI agent feature that can be installed into an [AIAgentFunctionalPipeline].
 *
 * @param TConfig The type of configuration required for the feature, extending [FeatureConfig].
 * @param TFeatureImpl The type representing the concrete implementation of the feature.
 */
public interface AIAgentFunctionalFeature<TConfig : FeatureConfig, TFeatureImpl : Any> : AIAgentFeature<TConfig, TFeatureImpl> {
    /**
     * Installs the feature into the specified [pipeline].
     * @return The implementation of the feature.
     */
    public fun install(config: TConfig, pipeline: AIAgentFunctionalPipeline): TFeatureImpl
}
