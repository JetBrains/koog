package ai.koog.agents.planner.goap

import ai.koog.agents.core.agent.context.AIAgentFunctionalContext

/**
 * Represents an action that can be performed by the agent.
 */
public class Action<State> internal constructor(
    internal val name: String,
    internal val description: String? = null,
    internal val precondition: State.() -> Boolean,
    internal val belief: State.() -> State,
    internal val cost: (State) -> Double,
    internal val execute: suspend (AIAgentFunctionalContext, State) -> State
)

/**
 * Represents a goal that the agent wants to achieve.
 */
public class Goal<State> internal constructor(
    internal val name: String,
    internal val description: String?,
    internal val value: (Double) -> Double,
    internal val cost: (State) -> Double,
    internal val condition: State.() -> Boolean
)

/**
 * A GOAP plan.
 */
public class GOAPPlan<State> internal constructor(
    internal val goal: Goal<State>,
    internal val actions: List<Action<State>>,
    internal val value: Double,
)
