package ai.koog.agents.core.feature

/**
 * Represents the context for intercepting or interacting with a specific feature in an AI agent pipeline.
 *
 * This context provides access to both the feature definition and its implementation, allowing for dynamic
 * modifications or inspections during the pipeline execution process.
 *
 * @param TFeature The type of the feature implementation associated with the context.
 * @property feature The feature definition, which provides the functionality and configuration capabilities.
 * @property featureImpl The specific implementation of the feature being executed or intercepted.
 */
public data class InterceptContext<TFeature : Any>(
    val feature: AIAgentFeature<*, TFeature>,
    val featureImpl: TFeature
)
