@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.feature.pipeline

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
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
import kotlin.time.Clock

/**
 * Represents a specific implementation of an AI agent pipeline that uses a planner approach.
 *
 * @property clock The clock used for time-based operations within the pipeline
 */
public expect open class AIAgentPlannerPipeline(
    agentConfig: AIAgentConfig,
    clock: Clock = Clock.System,
    basePipelineDelegate: AIAgentPipelineImpl = AIAgentPipelineImpl(agentConfig, clock)
) : AIAgentPipeline, AIAgentPlannerPipelineAPI {
    /**
     * Installs a non-graph feature into the pipeline with the provided configuration.
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

    @InternalAgentsApi
    public override suspend fun onPlanCreationStarting(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        context: AIAgentContext,
        state: Any,
        plan: Any?,
        stepIndex: Int,
    )

    @InternalAgentsApi
    public override suspend fun onPlanCreationCompleted(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        context: AIAgentContext,
        state: Any,
        plan: Any,
        stepIndex: Int,
    )

    @InternalAgentsApi
    public override suspend fun onStepExecutionStarting(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        context: AIAgentContext,
        state: Any,
        plan: Any,
        stepIndex: Int
    )

    @InternalAgentsApi
    public override suspend fun onStepExecutionCompleted(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        context: AIAgentContext,
        state: Any,
        plan: Any,
        stepIndex: Int,
    )

    @InternalAgentsApi
    public override suspend fun onPlanCompletionEvaluationStarting(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        context: AIAgentContext,
        state: Any,
        plan: Any,
        stepIndex: Int,
    )

    @InternalAgentsApi
    public override suspend fun onPlanCompletionEvaluationCompleted(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        context: AIAgentContext,
        state: Any,
        plan: Any,
        isCompleted: Boolean,
        stepIndex: Int,
    )

    public override fun interceptPlanCreationStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (PlanCreationStartingContext) -> Unit
    )

    public override fun interceptPlanCreationCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (PlanCreationCompletedContext) -> Unit
    )

    public override fun interceptStepExecutionStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (StepExecutionStartingContext) -> Unit
    )

    public override fun interceptStepExecutionCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (StepExecutionCompletedContext) -> Unit
    )

    public override fun interceptPlanCompletionEvaluationStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (PlanCompletionEvaluationStartingContext) -> Unit
    )

    public override fun interceptPlanCompletionEvaluationCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (PlanCompletionEvaluationCompletedContext) -> Unit
    )
}
