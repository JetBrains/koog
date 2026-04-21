package ai.koog.agents.core.feature.pipeline

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.handler.planner.PlanCompletionEvaluationCompletedContext
import ai.koog.agents.core.feature.handler.planner.PlanCompletionEvaluationStartingContext
import ai.koog.agents.core.feature.handler.planner.PlanCreationCompletedContext
import ai.koog.agents.core.feature.handler.planner.PlanCreationStartingContext
import ai.koog.agents.core.feature.handler.planner.StepExecutionCompletedContext
import ai.koog.agents.core.feature.handler.planner.StepExecutionStartingContext

/**
 * Platform-agnostic API for planner agent pipelines, extending the base pipeline API
 * with planner-specific functionality.
 */
public interface AIAgentPlannerPipelineAPI : AIAgentPipelineAPI {

    //region Trigger Planner Handlers

    /**
     * Notifies all registered planner handlers that a plan creation has started.
     *
     * @param eventId The unique identifier for the event group;
     * @param executionInfo The execution information for the plan creation event;
     * @param context The context of the plan creation;
     * @param plan The plan that is starting creation;
     */
    @InternalAgentsApi
    public suspend fun onPlanCreationStarting(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        context: AIAgentContext,
        state: Any,
        plan: Any?,
        stepIndex: Int,
    )

    /**
     * Notifies all registered planner handlers that a plan creation has completed.
     *
     * @param eventId The unique identifier for the event group;
     * @param executionInfo The execution information for the plan creation event;
     * @param context The context of the plan creation;
     * @param state The current state;
     * @param plan The plan that completed creation;
     */
    @InternalAgentsApi
    public suspend fun onPlanCreationCompleted(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        context: AIAgentContext,
        state: Any,
        plan: Any,
        stepIndex: Int,
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
    public suspend fun onStepExecutionStarting(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        context: AIAgentContext,
        state: Any,
        plan: Any,
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
     * @param stepIndex The index of the step in the plan;
     */
    @InternalAgentsApi
    public suspend fun onStepExecutionCompleted(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        context: AIAgentContext,
        state: Any,
        plan: Any,
        stepIndex: Int,
    )

    /**
     * Notifies all registered planner handlers when evaluating whether a plan is completed.
     *
     * @param eventId The unique identifier for the event group;
     * @param executionInfo The execution information for the evaluation event;
     * @param context The context of the plan execution;
     * @param state The current state;
     * @param plan The plan being evaluated for completion;
     */
    @InternalAgentsApi
    public suspend fun onPlanCompletionEvaluationStarting(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        context: AIAgentContext,
        state: Any,
        plan: Any,
        stepIndex: Int,
    )

    /**
     * Notifies all registered planner handlers when evaluating whether a plan is completed.
     *
     * @param eventId The unique identifier for the event group;
     * @param executionInfo The execution information for the evaluation event;
     * @param context The context of the plan execution;
     * @param state The current state;
     * @param plan The plan being evaluated for completion;
     * @param isCompleted The result of the completion check.
     */
    @InternalAgentsApi
    public suspend fun onPlanCompletionEvaluationCompleted(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        context: AIAgentContext,
        state: Any,
        plan: Any,
        isCompleted: Boolean,
        stepIndex: Int,
    )

    //endregion Trigger Planner Handlers

    //region Planner Interceptors

    /**
     * Intercepts plan creation starting event to perform actions when a plan begins creation.
     *
     * @param feature The feature associated with this handler;
     * @param handle A suspend function that processes the start of a plan creation.
     */
    public fun interceptPlanCreationStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (PlanCreationStartingContext) -> Unit
    )

    /**
     * Intercepts plan creation completed event to perform actions when a plan completes creation.
     *
     * @param feature The feature associated with this handler;
     * @param handle A suspend function that processes the completion of a plan creation.
     */
    public fun interceptPlanCreationCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (PlanCreationCompletedContext) -> Unit
    )

    /**
     * Intercepts step execution starting event to perform actions when a plan step begins execution.
     *
     * @param feature The feature associated with this handler;
     * @param handle A suspend function that processes the start of a step execution.
     *
     * Example:
     * ```
     * pipeline.interceptStepExecutionStarting(feature) { event ->
     *     logger.info("Step ${event.stepIndex} execution has started")
     * }
     * ```
     */
    public fun interceptStepExecutionStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (StepExecutionStartingContext) -> Unit
    )

    /**
     * Intercepts step execution completed event to perform actions when a plan step completes execution.
     *
     * @param feature The feature associated with this handler;
     * @param handle A suspend function that processes the completion of a step execution.
     *
     * Example:
     * ```
     * pipeline.interceptStepExecutionCompleted(feature) { event ->
     *     logger.info("Step ${event.stepIndex} execution has completed")
     * }
     * ```
     */
    public fun interceptStepExecutionCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (StepExecutionCompletedContext) -> Unit
    )

    /**
     * Intercepts plan completion evaluation starting event to perform actions when a plan is about to be evaluated for completion.
     *
     * @param feature The feature associated with this handler;
     * @param handle A suspend function that processes the start of a plan completion evaluation.
     */
    public fun interceptPlanCompletionEvaluationStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (PlanCompletionEvaluationStartingContext) -> Unit
    )

    /**
     * Intercepts plan completion evaluation completed event to perform actions when a plan is evaluated for completion.
     *
     * @param feature The feature associated with this handler;
     * @param handle A suspend function that processes the completion of a plan completion evaluation.
     *
     * Example:
     * ```
     * pipeline.interceptPlanCompletionEvaluationCompleted(feature) { event ->
     *     logger.info("Plan completion evaluated as: ${event.isCompleted}")
     * }
     * ```
     */
    public fun interceptPlanCompletionEvaluationCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (PlanCompletionEvaluationCompletedContext) -> Unit
    )

    //endregion Planner Interceptors
}
