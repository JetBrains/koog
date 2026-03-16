@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.feature.pipeline

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.feature.AIAgentPlannerFeature
import ai.koog.agents.core.feature.config.FeatureConfig
import kotlin.time.Clock

/**
 * Pipeline for AI agent planner execution, extending the functionality of [AIAgentPipelineCommon].
 *
 * This class uses a planner approach for agent execution, managing plan creation, step execution,
 * and completion evaluation lifecycle events. All planner lifecycle methods and interceptors are
 * inherited from [AIAgentPlannerPipelineCommon].
 *
 * Platform-specific implementations may add additional JVM/JS-specific interceptor overloads for Java interoperability.
 */
public expect open class AIAgentPlannerPipeline(
    agentConfig: AIAgentConfig,
    clock: Clock = Clock.System
) : AIAgentPlannerPipelineCommon {

    /**
     * Installs a planner feature into the pipeline with the provided configuration.
     *
     * @param TConfig The type of the feature configuration
     * @param TFeature The type of the feature being installed
     * @param feature The feature implementation to be installed
     * @param configure A lambda to customize the feature configuration
     */
    public fun <TConfig : FeatureConfig, TFeature : Any> install(
        feature: AIAgentPlannerFeature<TConfig, TFeature>,
        configure: TConfig.() -> Unit,
    )
}
