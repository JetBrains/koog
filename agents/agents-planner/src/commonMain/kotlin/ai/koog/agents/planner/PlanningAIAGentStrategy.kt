package ai.koog.agents.planner

import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import ai.koog.agents.core.agent.entity.AIAgentStrategy

/**
 * Represents a planning strategy for an AI agent.
 * Implementations need to define the following methods:
 * - [buildPlan] Creates a subgraph that builds a plan for the agent.
 * - [executeStep] Creates a subgraph that executes a step in the plan.
 * - [isPlanCompleted] Creates a subgraph that determines whether the plan is completed.
 *
 * @param name The name of the strategy.
 */
public abstract class PlanningAIAgentStrategy<State, Plan>(
    override val name: String,
) : AIAgentStrategy<State, State, AIAgentFunctionalContext> {
    /**
     * Builds a plan
     */
    public abstract suspend fun buildPlan(
        context: AIAgentFunctionalContext,
        state: State,
        plan: Plan?
    ): Plan

    /**
     * Executes a step in the plan.
     */
    public abstract suspend fun executeStep(
        context: AIAgentFunctionalContext,
        state: State,
        plan: Plan
    ): State

    /**
     * Checks if the plan is completed.
     */
    public abstract suspend fun isPlanCompleted(
        context: AIAgentFunctionalContext,
        state: State,
        plan: Plan
    ): Boolean

    override suspend fun execute(
        context: AIAgentFunctionalContext,
        input: State
    ): State? {
        var state = input
        var plan: Plan = buildPlan(context, state, null)

        while (!isPlanCompleted(context, state, plan)) {
            state = executeStep(context, state, plan)
            plan = buildPlan(context, state, plan)
        }

        return state
    }
}
