@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "MissingKDocForPublicAPI")
@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.core.feature.pipeline

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.AIAgentPlannerFeature
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.handler.planner.PlanCompletionEvaluationCompletedContext
import ai.koog.agents.core.feature.handler.planner.PlanCompletionEvaluationStartingContext
import ai.koog.agents.core.feature.handler.planner.PlanCreationCompletedContext
import ai.koog.agents.core.feature.handler.planner.PlanCreationStartingContext
import ai.koog.agents.core.feature.handler.planner.StepExecutionCompletedContext
import ai.koog.agents.core.feature.handler.planner.StepExecutionStartingContext
import ai.koog.agents.core.utils.submitToMainDispatcher
import kotlin.time.Clock

public actual open class AIAgentPlannerPipeline @JvmOverloads actual constructor(
    agentConfig: AIAgentConfig,
    clock: Clock,
    private val basePipelineDelegate: AIAgentPipelineImpl
) : AIAgentPipeline(agentConfig, clock), AIAgentPlannerPipelineAPI by AIAgentPlannerPipelineImpl(agentConfig, clock, basePipelineDelegate) {

    /**
     * Installs a non-graph feature into the pipeline with the provided configuration.
     *
     * @param TConfig The type of the feature configuration
     * @param TFeature The type of the feature being installed
     * @param feature The feature implementation to be installed
     * @param configure A lambda to customize the feature configuration
     */
    public actual fun <TConfig : FeatureConfig, TFeature : Any> install(
        feature: AIAgentPlannerFeature<TConfig, TFeature>,
        configure: TConfig.() -> Unit,
    ) {
        val featureConfig = feature.createInitialConfig().apply { configure() }
        val featureImpl = feature.install(
            config = featureConfig,
            pipeline = this,
        )

        basePipelineDelegate.install(feature.key, featureConfig, featureImpl)
    }

    //region JVM Planner Interceptors

    /**
     * Intercepts plan creation starting event to perform actions when a plan begins creation.
     *
     * JVM-friendly overload that accepts an async interceptor.
     */
    @JavaAPI
    @JvmName("interceptPlanCreationStarting")
    public fun javaApiInterceptPlanCreationStarting(
        feature: AIAgentFeature<*, *>,
        handle: Interceptor<PlanCreationStartingContext>
    ) {
        interceptPlanCreationStarting(feature) { ctx ->
            config.submitToMainDispatcher {
                handle.intercept(ctx)
            }
        }
    }

    /**
     * Intercepts plan creation completed event to perform actions when a plan completes creation.
     *
     * JVM-friendly overload that accepts an async interceptor.
     */
    @JavaAPI
    @JvmName("interceptPlanCreationCompleted")
    public fun javaApiInterceptPlanCreationCompleted(
        feature: AIAgentFeature<*, *>,
        handle: Interceptor<PlanCreationCompletedContext>
    ) {
        interceptPlanCreationCompleted(feature) { ctx ->
            config.submitToMainDispatcher {
                handle.intercept(ctx)
            }
        }
    }

    /**
     * Intercepts step execution starting event to perform actions when a plan step begins execution.
     *
     * JVM-friendly overload that accepts an async interceptor.
     *
     * Example (Java):
     * pipeline.interceptStepExecutionStarting(feature, event -> {
     *     // Step execution started
     *     return java.util.concurrent.CompletableFuture.completedFuture(null);
     * });
     */
    @JavaAPI
    @JvmName("interceptStepExecutionStarting")
    public fun javaApiInterceptStepExecutionStarting(
        feature: AIAgentFeature<*, *>,
        handle: Interceptor<StepExecutionStartingContext>
    ) {
        interceptStepExecutionStarting(feature) { ctx ->
            config.submitToMainDispatcher {
                handle.intercept(ctx)
            }
        }
    }

    /**
     * Intercepts step execution completed event to perform actions when a plan step completes execution.
     *
     * JVM-friendly overload that accepts an async interceptor.
     *
     * Example (Java):
     * pipeline.interceptStepExecutionCompleted(feature, event -> {
     *     // Step execution completed
     *     return java.util.concurrent.CompletableFuture.completedFuture(null);
     * });
     */
    @JavaAPI
    @JvmName("interceptStepExecutionCompleted")
    public fun javaApiInterceptStepExecutionCompleted(
        feature: AIAgentFeature<*, *>,
        handle: Interceptor<StepExecutionCompletedContext>
    ) {
        interceptStepExecutionCompleted(feature) { ctx ->
            config.submitToMainDispatcher {
                handle.intercept(ctx)
            }
        }
    }

    /**
     * Intercepts plan completion evaluation starting event to perform actions when a plan is about to be evaluated for completion.
     *
     * JVM-friendly overload that accepts an async interceptor.
     */
    @JavaAPI
    @JvmName("interceptPlanCompletionEvaluationStarting")
    public fun javaApiInterceptPlanCompletionEvaluationStarting(
        feature: AIAgentFeature<*, *>,
        handle: Interceptor<PlanCompletionEvaluationStartingContext>
    ) {
        interceptPlanCompletionEvaluationStarting(feature) { ctx ->
            config.submitToMainDispatcher {
                handle.intercept(ctx)
            }
        }
    }

    /**
     * Intercepts plan completion evaluation completed event to perform actions when evaluating if a plan is completed.
     *
     * JVM-friendly overload that accepts an async interceptor.
     *
     * Example (Java):
     * pipeline.interceptPlanCompletionEvaluationCompleted(feature, event -> {
     *     // Plan completion evaluated
     *     return java.util.concurrent.CompletableFuture.completedFuture(null);
     * });
     */
    @JavaAPI
    @JvmName("interceptPlanCompletionEvaluationCompleted")
    public fun javaApiInterceptPlanCompletionEvaluationCompleted(
        feature: AIAgentFeature<*, *>,
        handle: Interceptor<PlanCompletionEvaluationCompletedContext>
    ) {
        interceptPlanCompletionEvaluationCompleted(feature) { ctx ->
            config.submitToMainDispatcher {
                handle.intercept(ctx)
            }
        }
    }

    //endregion JVM Planner Interceptors
}
