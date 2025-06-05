package ai.koog.agents.core.feature

/**
 * Represents the context in which an AI agent feature operates. This class holds a reference
 * to the feature and its specific implementation. It is used to provide scoped access to the
 * feature's capabilities and configuration within the agent pipeline.
 *
 * @param TFeature The type of the feature implementation.
 * @property feature The AI agent feature associated with this context. Provides details about
 * the feature and its configuration mechanisms.
 * @property featureImpl The specific implementation of the feature being used within the
 * pipeline.
 */
public data class InterceptContext<TFeature : Any>(
    val feature: AIAgentFeature<*, TFeature>,
    val featureImpl: TFeature
)
