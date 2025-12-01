package ai.koog.agents.planner.goap

import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegate

/**
 * Represents an action that can be performed by the agent.
 */
public class Action<State> @PublishedApi internal constructor(
    internal val name: String,
    internal val description: String? = null,
    internal val precondition: State.() -> Boolean,
    internal val belief: State.() -> State,
    internal val cost: (State) -> Double,
    @PublishedApi
    internal val define: AIAgentSubgraphBuilderBase<*, *>.() -> AIAgentSubgraphDelegate<State, State>
)

/**
 * Represents a goal that the agent wants to achieve.
 */
public class Goal<State> internal constructor(
    internal val name: String,
    internal val description: String?,
    internal val value: (Double) -> Double,
    internal val cost: (State) -> Double,
    @PublishedApi
    internal val condition: State.() -> Boolean
)

/**
 * A GOAP plan.
 */
public class GOAPPlan<State> internal constructor(
    @PublishedApi
    internal val goal: Goal<State>,
    @PublishedApi
    internal val actions: List<Action<State>>,
    @PublishedApi
    internal val value: Double,
)
