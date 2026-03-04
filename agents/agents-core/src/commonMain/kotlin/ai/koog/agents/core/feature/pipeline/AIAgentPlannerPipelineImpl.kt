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
 * Default implementation of [AIAgentPlannerPipelineAPI] that delegates base pipeline operations
 * to [AIAgentPipelineImpl] and implements planner-specific functionality.
 */
public class AIAgentPlannerPipelineImpl(
    config: AIAgentConfig,
    clock: Clock = kotlin.time.Clock.System,
    private val basePipelineDelegate: AIAgentPipelineImpl
) : AIAgentPlannerPipelineAPI, AIAgentPipelineAPI by basePipelineDelegate {

    //region Invoke Planner Handlers

    @InternalAgentsApi
    public override suspend fun onPlanCreationStarting(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        context: AIAgentContext,
        state: Any,
        plan: Any?,
        stepIndex: Int,
    ) {
        basePipelineDelegate.invokeRegisteredHandlersForEvent(
            eventType = AgentLifecycleEventType.BuildPlanStarting,
            context = PlanCreationStartingContext(eventId, executionInfo, context, state, plan, stepIndex)
        )
    }

    @InternalAgentsApi
    public override suspend fun onPlanCreationCompleted(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        context: AIAgentContext,
        state: Any,
        plan: Any,
        stepIndex: Int,
    ) {
        basePipelineDelegate.invokeRegisteredHandlersForEvent(
            eventType = AgentLifecycleEventType.BuildPlanCompleted,
            context = PlanCreationCompletedContext(eventId, executionInfo, context, state, null, plan, stepIndex)
        )
    }

    @InternalAgentsApi
    public override suspend fun onStepExecutionStarting(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        context: AIAgentContext,
        state: Any,
        plan: Any,
        stepIndex: Int
    ) {
        basePipelineDelegate.invokeRegisteredHandlersForEvent(
            eventType = AgentLifecycleEventType.ExecuteStepStarting,
            context = StepExecutionStartingContext(eventId, executionInfo, context, state, plan, stepIndex)
        )
    }

    @InternalAgentsApi
    public override suspend fun onStepExecutionCompleted(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        context: AIAgentContext,
        state: Any,
        plan: Any,
        stepIndex: Int,
    ) {
        basePipelineDelegate.invokeRegisteredHandlersForEvent(
            eventType = AgentLifecycleEventType.ExecuteStepCompleted,
            context = StepExecutionCompletedContext(eventId, executionInfo, context, state, plan, stepIndex)
        )
    }

    @InternalAgentsApi
    public override suspend fun onPlanCompletionEvaluationStarting(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        context: AIAgentContext,
        state: Any,
        plan: Any,
        stepIndex: Int,
    ) {
        basePipelineDelegate.invokeRegisteredHandlersForEvent(
            eventType = AgentLifecycleEventType.IsPlanCompletedStarting,
            context = PlanCompletionEvaluationStartingContext(eventId, executionInfo, context, state, plan, stepIndex)
        )
    }

    @InternalAgentsApi
    public override suspend fun onPlanCompletionEvaluationCompleted(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        context: AIAgentContext,
        state: Any,
        plan: Any,
        isCompleted: Boolean,
        stepIndex: Int,
    ) {
        basePipelineDelegate.invokeRegisteredHandlersForEvent(
            eventType = AgentLifecycleEventType.IsPlanCompletedCompleted,
            context = PlanCompletionEvaluationCompletedContext(eventId, executionInfo, context, state, plan, isCompleted, stepIndex)
        )
    }

    //endregion Invoke Planner Handlers

    //region Planner Interceptors

    @OptIn(InternalAgentsApi::class)
    public override fun interceptPlanCreationStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (PlanCreationStartingContext) -> Unit
    ) {
        basePipelineDelegate.addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.BuildPlanStarting,
            handler = basePipelineDelegate.createConditionalHandler(feature, handle)
        )
    }

    @OptIn(InternalAgentsApi::class)
    public override fun interceptPlanCreationCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (PlanCreationCompletedContext) -> Unit
    ) {
        basePipelineDelegate.addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.BuildPlanCompleted,
            handler = basePipelineDelegate.createConditionalHandler(feature, handle)
        )
    }

    @OptIn(InternalAgentsApi::class)
    public override fun interceptStepExecutionStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (StepExecutionStartingContext) -> Unit
    ) {
        basePipelineDelegate.addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.ExecuteStepStarting,
            handler = basePipelineDelegate.createConditionalHandler(feature, handle)
        )
    }

    @OptIn(InternalAgentsApi::class)
    public override fun interceptStepExecutionCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (StepExecutionCompletedContext) -> Unit
    ) {
        basePipelineDelegate.addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.ExecuteStepCompleted,
            handler = basePipelineDelegate.createConditionalHandler(feature, handle)
        )
    }

    @OptIn(InternalAgentsApi::class)
    public override fun interceptPlanCompletionEvaluationStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (PlanCompletionEvaluationStartingContext) -> Unit
    ) {
        basePipelineDelegate.addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.IsPlanCompletedStarting,
            handler = basePipelineDelegate.createConditionalHandler(feature, handle)
        )
    }

    @OptIn(InternalAgentsApi::class)
    public override fun interceptPlanCompletionEvaluationCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (PlanCompletionEvaluationCompletedContext) -> Unit
    ) {
        basePipelineDelegate.addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.IsPlanCompletedCompleted,
            handler = basePipelineDelegate.createConditionalHandler(feature, handle)
        )
    }

    //endregion Planner Interceptors
}
