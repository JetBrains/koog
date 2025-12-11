package ai.koog.agents.planner.goap

import ai.koog.agents.core.agent.context.AIAgentFunctionalContext

/**
 * Represents an action that can be performed by the agent.
 */
public class Action<State>(
    public val name: String,
    public val description: String? = null,
    public val precondition: (State) -> Boolean,
    public val belief: (State) -> State,
    public val cost: (State) -> Double,
    public val execute: suspend (AIAgentFunctionalContext, State) -> State
)

/**
 * Represents a goal that the agent wants to achieve.
 */
public class Goal<State>(
    public val name: String,
    public val description: String?,
    public val value: (Double) -> Double,
    public val cost: (State) -> Double,
    public val condition: (State) -> Boolean
)

/**
 * A GOAP plan.
 */
public class GOAPPlan<State>(
    public val goal: Goal<State>,
    public val actions: List<Action<State>>,
    public val value: Double,
)
