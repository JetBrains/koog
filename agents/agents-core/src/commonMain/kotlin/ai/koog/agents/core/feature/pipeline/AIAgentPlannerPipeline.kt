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
import ai.koog.serialization.TypeToken
import ai.koog.utils.time.KoogClock

/**
 * Represents a specific implementation of an AI agent pipeline that uses a planner approach.
 *
 * @property clock The clock used for time-based operations within the pipeline.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public expect open class AIAgentPlannerPipeline(
    agentConfig: AIAgentConfig,
    clock: KoogClock = KoogClock.System,
    basePipelineDelegate: AIAgentPipelineImpl = AIAgentPipelineImpl(agentConfig, clock)
) : AIAgentPipeline, AIAgentPlannerPipelineAPI {
    /**
     * Installs a planner feature into the pipeline with the provided configuration.
     *
     * @param TConfig The type of the feature configuration;
     * @param TFeature The type of the feature being installed;
     * @param feature The feature implementation to be installed;
     * @param configure A lambda to customize the feature configuration.
     */
    public fun <TConfig : FeatureConfig, TFeature : Any> install(
        feature: AIAgentPlannerFeature<TConfig, TFeature>,
        configure: TConfig.() -> Unit,
    )

    //region Trigger Planner Handlers

    /**
     * Notifies all registered planner handlers that a plan creation has started.
     *
     * @param eventId The unique identifier for the event group;
     * @param executionInfo The execution information for the plan creation event;
     * @param context The context of the plan creation;
     * @param state The current state;
     * @param plan The current plan, or `null` if this is the first plan;
     * @param stepIndex The index of the step in the plan.
     */
    @InternalAgentsApi
    public override suspend fun onPlanCreationStarting(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        context: AIAgentContext,
        state: Any,
        stateType: TypeToken?,
        plan: Any?,
        planType: TypeToken?,
        stepIndex: Int,
    )

    /**
     * Notifies all registered planner handlers that a plan creation has completed.
     *
     * @param eventId The unique identifier for the event group;
     * @param executionInfo The execution information for the plan creation event;
     * @param context The context of the plan creation;
     * @param state The current state;
     * @param plan The previous plan, or `null` if this is the first plan;
     * @param stepIndex The index of the step in the plan;
     * @param updatedPlan The newly built plan.
     */
    @InternalAgentsApi
    public override suspend fun onPlanCreationCompleted(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        context: AIAgentContext,
        state: Any,
        stateType: TypeToken?,
        plan: Any?,
        planType: TypeToken?,
        stepIndex: Int,
        updatedPlan: Any,
    )

    /**
     * Notifies all registered planner handlers that a plan step execution has started.
     *
     * @param eventId The unique identifier for the event group;
     * @param executionInfo The execution information for the step execution event;
     * @param context The context of the step execution;
     * @param state The current state;
     * @param plan The plan that is starting execution;
     * @param stepIndex The index of the step in the plan.
     */
    @InternalAgentsApi
    public override suspend fun onStepExecutionStarting(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        context: AIAgentContext,
        state: Any,
        stateType: TypeToken?,
        plan: Any,
        planType: TypeToken?,
        stepIndex: Int
    )

    /**
     * Notifies all registered planner handlers that a plan step execution has completed.
     *
     * @param eventId The unique identifier for the event group;
     * @param executionInfo The execution information for the step execution event;
     * @param context The context of the step execution;
     * @param state The state after step execution;
     * @param plan The plan that completed execution;
     * @param stepIndex The index of the step in the plan.
     */
    @InternalAgentsApi
    public override suspend fun onStepExecutionCompleted(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        context: AIAgentContext,
        state: Any,
        stateType: TypeToken?,
        plan: Any,
        planType: TypeToken?,
        stepIndex: Int,
    )

    /**
     * Notifies all registered planner handlers when a plan-completion check is about to be evaluated.
     *
     * @param eventId The unique identifier for the event group;
     * @param executionInfo The execution information for the evaluation event;
     * @param context The context of the plan execution;
     * @param state The current state;
     * @param plan The plan being evaluated for completion;
     * @param stepIndex The index of the step in the plan.
     */
    @InternalAgentsApi
    public override suspend fun onPlanCompletionEvaluationStarting(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        context: AIAgentContext,
        state: Any,
        stateType: TypeToken?,
        plan: Any,
        planType: TypeToken?,
        stepIndex: Int,
    )

    /**
     * Notifies all registered planner handlers when a plan-completion check has been evaluated.
     *
     * @param eventId The unique identifier for the event group;
     * @param executionInfo The execution information for the evaluation event;
     * @param context The context of the plan execution;
     * @param state The current state;
     * @param plan The plan being evaluated for completion;
     * @param stepIndex The index of the step in the plan;
     * @param isCompleted The result of the completion check.
     */
    @InternalAgentsApi
    public override suspend fun onPlanCompletionEvaluationCompleted(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        context: AIAgentContext,
        state: Any,
        stateType: TypeToken?,
        plan: Any,
        planType: TypeToken?,
        stepIndex: Int,
        isCompleted: Boolean,
    )

    //endregion Trigger Planner Handlers

    //region Planner Interceptors

    /**
     * Intercepts plan creation starting event to perform actions when a plan begins creation.
     *
     * @param feature The feature associated with this handler;
     * @param handle A suspend function that processes the start of a plan creation.
     */
    public override fun interceptPlanCreationStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (PlanCreationStartingContext) -> Unit
    )

    /**
     * Intercepts plan creation completed event to perform actions when a plan completes creation.
     *
     * @param feature The feature associated with this handler;
     * @param handle A suspend function that processes the completion of a plan creation.
     */
    public override fun interceptPlanCreationCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (PlanCreationCompletedContext) -> Unit
    )

    /**
     * Intercepts step execution starting event to perform actions when a plan step begins execution.
     *
     * @param feature The feature associated with this handler;
     * @param handle A suspend function that processes the start of a step execution.
     */
    public override fun interceptStepExecutionStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (StepExecutionStartingContext) -> Unit
    )

    /**
     * Intercepts step execution completed event to perform actions when a plan step completes execution.
     *
     * @param feature The feature associated with this handler;
     * @param handle A suspend function that processes the completion of a step execution.
     */
    public override fun interceptStepExecutionCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (StepExecutionCompletedContext) -> Unit
    )

    /**
     * Intercepts plan completion evaluation starting event to perform actions when a plan is about to be evaluated for completion.
     *
     * @param feature The feature associated with this handler;
     * @param handle A suspend function that processes the start of a plan completion evaluation.
     */
    public override fun interceptPlanCompletionEvaluationStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (PlanCompletionEvaluationStartingContext) -> Unit
    )

    /**
     * Intercepts plan completion evaluation completed event to perform actions when a plan is evaluated for completion.
     *
     * @param feature The feature associated with this handler;
     * @param handle A suspend function that processes the completion of a plan completion evaluation.
     */
    public override fun interceptPlanCompletionEvaluationCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (PlanCompletionEvaluationCompletedContext) -> Unit
    )

    //endregion Planner Interceptors
}
