@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.core.feature.pipeline

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.handler.AgentLifecycleEventType
import ai.koog.agents.core.feature.handler.planner.PlanCompletionEvaluationCompletedContext
import ai.koog.agents.core.feature.handler.planner.PlanCompletionEvaluationStartingContext
import ai.koog.agents.core.feature.handler.planner.PlanCreationCompletedContext
import ai.koog.agents.core.feature.handler.planner.PlanCreationStartingContext
import ai.koog.agents.core.feature.handler.planner.StepExecutionCompletedContext
import ai.koog.agents.core.feature.handler.planner.StepExecutionStartingContext
import kotlin.time.Clock

/**
 * Base implementation for planner-specific AI agent pipeline operations.
 *
 * Extends [AIAgentPipelineCommon] to provide plan creation, step execution, and completion
 * evaluation lifecycle management for planner-based agents.
 *
 * @property clock Clock instance for time-based operations
 */
public abstract class AIAgentPlannerPipelineCommon(
    config: AIAgentConfig,
    clock: Clock = Clock.System
) : AIAgentPipeline(config, clock) {

    //region Invoke Planner Handlers

    /**
     * Notifies all registered handlers that plan creation is starting.
     *
     * @param eventId The unique identifier for the event group
     * @param executionInfo The execution information for the event
     * @param context The agent context
     * @param state The current state
     * @param plan The current plan (may be null if no plan exists yet)
     * @param stepIndex The current step index
     */
    internal suspend fun onPlanCreationStarting(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        context: AIAgentContext,
        state: Any,
        plan: Any?,
        stepIndex: Int,
    ) {
        invokeRegisteredHandlersForEvent(
            eventType = AgentLifecycleEventType.BuildPlanStarting,
            context = PlanCreationStartingContext(eventId, executionInfo, context, state, plan, stepIndex)
        )
    }

    /**
     * Notifies all registered handlers that plan creation has completed.
     *
     * @param eventId The unique identifier for the event group
     * @param executionInfo The execution information for the event
     * @param context The agent context
     * @param state The current state
     * @param plan The created plan
     * @param stepIndex The current step index
     */
    internal suspend fun onPlanCreationCompleted(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        context: AIAgentContext,
        state: Any,
        plan: Any,
        stepIndex: Int,
    ) {
        invokeRegisteredHandlersForEvent(
            eventType = AgentLifecycleEventType.BuildPlanCompleted,
            context = PlanCreationCompletedContext(eventId, executionInfo, context, state, null, plan, stepIndex)
        )
    }

    /**
     * Notifies all registered handlers that step execution is starting.
     *
     * @param eventId The unique identifier for the event group
     * @param executionInfo The execution information for the event
     * @param context The agent context
     * @param state The current state
     * @param plan The current plan
     * @param stepIndex The index of the step being executed
     */
    internal suspend fun onStepExecutionStarting(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        context: AIAgentContext,
        state: Any,
        plan: Any,
        stepIndex: Int
    ) {
        invokeRegisteredHandlersForEvent(
            eventType = AgentLifecycleEventType.ExecuteStepStarting,
            context = StepExecutionStartingContext(eventId, executionInfo, context, state, plan, stepIndex)
        )
    }

    /**
     * Notifies all registered handlers that step execution has completed.
     *
     * @param eventId The unique identifier for the event group
     * @param executionInfo The execution information for the event
     * @param context The agent context
     * @param state The current state
     * @param plan The current plan
     * @param stepIndex The index of the completed step
     */
    internal suspend fun onStepExecutionCompleted(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        context: AIAgentContext,
        state: Any,
        plan: Any,
        stepIndex: Int,
    ) {
        invokeRegisteredHandlersForEvent(
            eventType = AgentLifecycleEventType.ExecuteStepCompleted,
            context = StepExecutionCompletedContext(eventId, executionInfo, context, state, plan, stepIndex)
        )
    }

    /**
     * Notifies all registered handlers that plan completion evaluation is starting.
     *
     * @param eventId The unique identifier for the event group
     * @param executionInfo The execution information for the event
     * @param context The agent context
     * @param state The current state
     * @param plan The current plan being evaluated
     * @param stepIndex The current step index
     */
    internal suspend fun onPlanCompletionEvaluationStarting(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        context: AIAgentContext,
        state: Any,
        plan: Any,
        stepIndex: Int,
    ) {
        invokeRegisteredHandlersForEvent(
            eventType = AgentLifecycleEventType.IsPlanCompletedStarting,
            context = PlanCompletionEvaluationStartingContext(eventId, executionInfo, context, state, plan, stepIndex)
        )
    }

    /**
     * Notifies all registered handlers that plan completion evaluation has completed.
     *
     * @param eventId The unique identifier for the event group
     * @param executionInfo The execution information for the event
     * @param context The agent context
     * @param state The current state
     * @param plan The current plan
     * @param isCompleted Whether the plan is considered completed
     * @param stepIndex The current step index
     */
    internal suspend fun onPlanCompletionEvaluationCompleted(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        context: AIAgentContext,
        state: Any,
        plan: Any,
        isCompleted: Boolean,
        stepIndex: Int,
    ) {
        invokeRegisteredHandlersForEvent(
            eventType = AgentLifecycleEventType.IsPlanCompletedCompleted,
            context = PlanCompletionEvaluationCompletedContext(eventId, executionInfo, context, state, plan, isCompleted, stepIndex)
        )
    }

    //endregion Invoke Planner Handlers

    //region Planner Interceptors

    /**
     * Intercepts plan creation before it starts.
     *
     * @param feature The feature associated with this handler
     * @param handle The handler that processes plan creation starting events
     */
    public fun interceptPlanCreationStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (PlanCreationStartingContext) -> Unit
    ) {
        addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.BuildPlanStarting,
            handler = createConditionalHandler(feature, handle)
        )
    }

    /**
     * Intercepts plan creation after it completes.
     *
     * @param feature The feature associated with this handler
     * @param handle The handler that processes plan creation completed events
     */
    public fun interceptPlanCreationCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (PlanCreationCompletedContext) -> Unit
    ) {
        addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.BuildPlanCompleted,
            handler = createConditionalHandler(feature, handle)
        )
    }

    /**
     * Intercepts step execution before it starts.
     *
     * @param feature The feature associated with this handler
     * @param handle The handler that processes step execution starting events
     */
    public fun interceptStepExecutionStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (StepExecutionStartingContext) -> Unit
    ) {
        addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.ExecuteStepStarting,
            handler = createConditionalHandler(feature, handle)
        )
    }

    /**
     * Intercepts step execution after it completes.
     *
     * @param feature The feature associated with this handler
     * @param handle The handler that processes step execution completed events
     */
    public fun interceptStepExecutionCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (StepExecutionCompletedContext) -> Unit
    ) {
        addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.ExecuteStepCompleted,
            handler = createConditionalHandler(feature, handle)
        )
    }

    /**
     * Intercepts plan completion evaluation before it starts.
     *
     * @param feature The feature associated with this handler
     * @param handle The handler that processes plan completion evaluation starting events
     */
    public fun interceptPlanCompletionEvaluationStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (PlanCompletionEvaluationStartingContext) -> Unit
    ) {
        addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.IsPlanCompletedStarting,
            handler = createConditionalHandler(feature, handle)
        )
    }

    /**
     * Intercepts plan completion evaluation after it completes.
     *
     * @param feature The feature associated with this handler
     * @param handle The handler that processes plan completion evaluation completed events
     */
    public fun interceptPlanCompletionEvaluationCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (PlanCompletionEvaluationCompletedContext) -> Unit
    ) {
        addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.IsPlanCompletedCompleted,
            handler = createConditionalHandler(feature, handle)
        )
    }

    //endregion Planner Interceptors
}
