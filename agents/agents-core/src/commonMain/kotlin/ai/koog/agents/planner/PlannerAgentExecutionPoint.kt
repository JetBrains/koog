package ai.koog.agents.planner

import kotlinx.serialization.Serializable

/**
 * Represents the execution point of the planner agent.
 */
@Serializable
public sealed interface PlannerAgentExecutionPoint {

    /**
     * Dummy execution point.
     */
    @Serializable
    public object Dummy : PlannerAgentExecutionPoint

    /**
     * Execution point immediately after the plan is created.
     */
    @Serializable
    public object PlanCreated : PlannerAgentExecutionPoint

    /**
     * Execution point immediately after a step is executed.
     */
    @Serializable
    public object StepExecuted : PlannerAgentExecutionPoint

    /**
     * Execution point immediately after the plan completion is evaluated.
     */
    @Serializable
    public data class PlanCompletionEvaluated(val isCompleted: Boolean) : PlannerAgentExecutionPoint
}
