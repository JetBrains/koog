package ai.koog.agents.core.feature.handler.planner

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.feature.handler.AgentLifecycleEventContext
import ai.koog.agents.core.feature.handler.AgentLifecycleEventType
import ai.koog.serialization.TypeToken

/**
 * Defines the context specifically for handling planner-related events within the AI agent framework.
 * Extends the base event handler context to include functionality and behavior dedicated to managing
 * the lifecycle and operations of planner agents.
 */
public interface PlannerEventContext : AgentLifecycleEventContext {
    /**
     * The Agent context associated with the planner execution.
     */
    public val context: AIAgentContext

    /**
     * The current state of the planner execution.
     */
    public val state: Any

    /**
     * The [TypeToken] of the state
     */
    public val stateType: TypeToken?

    /**
     * The current plan being executed or planned.
     */
    public val plan: Any?

    /**
     * The [TypeToken] of the plan
     */
    public val planType: TypeToken?

    /**
     * The index of the current step within the plan.
     */
    public val stepIndex: Int
}

/**
 * Represents the context for a buildPlan operation starting event.
 */
public class PlanCreationStartingContext(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    override val context: AIAgentContext,
    override val state: Any,
    override val stateType: TypeToken?,
    override val plan: Any?,
    override val planType: TypeToken?,
    override val stepIndex: Int,
) : PlannerEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.BuildPlanStarting
}

/**
 * Represents the context for a buildPlan operation completed event.
 *
 * @property updatedPlan The newly built plan.
 */
public class PlanCreationCompletedContext(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    override val context: AIAgentContext,
    override val state: Any,
    override val stateType: TypeToken?,
    override val plan: Any?,
    override val planType: TypeToken?,
    override val stepIndex: Int,
    public val updatedPlan: Any,
) : PlannerEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.BuildPlanCompleted
}

/**
 * Represents the context for an executeStep operation starting event.
 */
public class StepExecutionStartingContext(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    override val context: AIAgentContext,
    override val state: Any,
    override val stateType: TypeToken?,
    override val plan: Any,
    override val planType: TypeToken?,
    override val stepIndex: Int,
) : PlannerEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.ExecuteStepStarting
}

/**
 * Represents the context for an executeStep operation completed event.
 */
public class StepExecutionCompletedContext(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    override val context: AIAgentContext,
    override val state: Any,
    override val stateType: TypeToken?,
    override val plan: Any,
    override val planType: TypeToken?,
    override val stepIndex: Int,
) : PlannerEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.ExecuteStepCompleted
}

/**
 * Represents the context for an isPlanCompleted check starting event.
 */
public class PlanCompletionEvaluationStartingContext(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    override val context: AIAgentContext,
    override val state: Any,
    override val stateType: TypeToken?,
    override val plan: Any,
    override val planType: TypeToken?,
    override val stepIndex: Int,
) : PlannerEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.IsPlanCompletedStarting
}

/**
 * Represents the context for an isPlanCompleted check completed event.
 *
 * @property isCompleted The result of the completion check.
 */
public class PlanCompletionEvaluationCompletedContext(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    override val context: AIAgentContext,
    override val state: Any,
    override val stateType: TypeToken?,
    override val plan: Any,
    override val planType: TypeToken?,
    override val stepIndex: Int,
    public val isCompleted: Boolean,
) : PlannerEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.IsPlanCompletedCompleted
}
