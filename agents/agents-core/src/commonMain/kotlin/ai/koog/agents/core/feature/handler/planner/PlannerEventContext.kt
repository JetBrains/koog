package ai.koog.agents.core.feature.handler.planner

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.feature.handler.AgentLifecycleEventContext
import ai.koog.agents.core.feature.handler.AgentLifecycleEventType

/**
 * Defines the context specifically for handling planner-related events within the AI agent framework.
 * Extends the base event handler context to include functionality and behavior dedicated to managing
 * the lifecycle and operations of planner agents.
 */
public interface PlannerEventContext : AgentLifecycleEventContext

/**
 * Represents the context for a buildPlan operation starting event.
 *
 * @property eventId The unique identifier for the event;
 * @property executionInfo The execution information containing parentId and current execution path;
 * @property context The context associated with the planner's execution;
 * @property state The current state;
 * @property currentPlan The current plan (null on first call).
 */
public class PlanCreationStartingContext(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    public val context: AIAgentContext,
    public val state: Any,
    public val currentPlan: Any?,
    public val stepIndex: Int,
) : PlannerEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.BuildPlanStarting
}

/**
 * Represents the context for a buildPlan operation completed event.
 *
 * @property eventId The unique identifier for the event;
 * @property executionInfo The execution information containing parentId and current execution path;
 * @property context The context associated with the planner's execution;
 * @property state The current state;
 * @property currentPlan The previous plan (null on first call);
 * @property newPlan The newly built plan.
 */
public class PlanCreationCompletedContext(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    public val context: AIAgentContext,
    public val state: Any,
    public val currentPlan: Any?,
    public val newPlan: Any,
    public val stepIndex: Int,
) : PlannerEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.BuildPlanCompleted
}

/**
 * Represents the context for an executeStep operation starting event.
 *
 * @property eventId The unique identifier for the event;
 * @property executionInfo The execution information containing parentId and current execution path;
 * @property context The context associated with the planner's execution;
 * @property state The current state;
 * @property plan The current plan.
 */
public class StepExecutionStartingContext(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    public val context: AIAgentContext,
    public val state: Any,
    public val plan: Any,
    public val stepIndex: Int,
) : PlannerEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.ExecuteStepStarting
}

/**
 * Represents the context for an executeStep operation completed event.
 *
 * @property eventId The unique identifier for the event;
 * @property executionInfo The execution information containing parentId and current execution path;
 * @property context The context associated with the planner's execution;
 * @property state The state after step execution;
 * @property plan The current plan;
 */
public class StepExecutionCompletedContext(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    public val context: AIAgentContext,
    public val state: Any,
    public val plan: Any,
    public val stepIndex: Int,
) : PlannerEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.ExecuteStepCompleted
}

/**
 * Represents the context for an isPlanCompleted check starting event.
 *
 * @property eventId The unique identifier for the event;
 * @property executionInfo The execution information containing parentId and current execution path;
 * @property context The context associated with the planner's execution;
 * @property state The current state;
 * @property plan The current plan.
 */
public class PlanCompletionEvaluationStartingContext(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    public val context: AIAgentContext,
    public val state: Any,
    public val plan: Any,
    public val stepIndex: Int,
) : PlannerEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.IsPlanCompletedStarting
}

/**
 * Represents the context for an isPlanCompleted check completed event.
 *
 * @property eventId The unique identifier for the event;
 * @property executionInfo The execution information containing parentId and current execution path;
 * @property context The context associated with the planner's execution;
 * @property state The current state;
 * @property plan The current plan;
 * @property isCompleted The result of the completion check.
 */
public class PlanCompletionEvaluationCompletedContext(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    public val context: AIAgentContext,
    public val state: Any,
    public val plan: Any,
    public val isCompleted: Boolean,
    public val stepIndex: Int,
) : PlannerEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.IsPlanCompletedCompleted
}
