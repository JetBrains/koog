package ai.koog.agents.core.feature.pipeline

import ai.koog.agents.core.feature.config.FeatureConfig

/**
 * Represents configured and installed agent feature implementation along with its configuration.
 * @param featureImpl The feature implementation
 * @param featureConfig The feature configuration
 */
@Suppress("RedundantVisibilityModifier") // have to put public here, explicitApi requires it
public class RegisteredFeature(
    public val featureImpl: Any,
    public val featureConfig: FeatureConfig
)
