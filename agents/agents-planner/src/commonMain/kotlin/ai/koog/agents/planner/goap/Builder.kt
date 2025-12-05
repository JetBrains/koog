package ai.koog.agents.planner.goap

import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import kotlin.math.exp

/**
 * DSL for defining actions.
 */
public class GOAPPlannerBuilder<State> internal constructor(
    internal val name: String
) {
    internal val actions: MutableList<Action<State>> = mutableListOf()

    internal val goals: MutableList<Goal<State>> = mutableListOf()

    /**
     * Defines an action available to the GOAP agent.
     *
     * @param name The name of the action.
     * @param description Optional description of the action.
     * @param precondition Condition determining if the action can be performed.
     * @param belief Optimistic belief of the state after performing the action.
     * @param cost Heuristic estimate for the cost of performing the action. Default is 1.0.
     * @param execute Subgraph defining how the action is performed.
     */
    public fun action(
        name: String,
        description: String? = null,
        precondition: State.() -> Boolean,
        belief: State.() -> State,
        cost: (State) -> Double = { 1.0 },
        execute: suspend (AIAgentFunctionalContext, State) -> State
    ) {
        actions.add(Action(name, description, precondition, belief, cost, execute))
    }

    /**
     * Defines a goal for the GOAP agent.
     *
     * @param name The name of the goal.
     * @param description Optional description of the goal.
     * @param value Goal value depending on the cost of reaching the goal. Default is `exp(-cost)`.
     * @param cost Heuristic estimate for the cost of reaching the goal. Default is 1.0.
     * @param condition Condition determining when the goal is achieved.
     */
    public fun goal(
        name: String,
        description: String? = null,
        value: (Double) -> Double = { cost -> exp(-cost) },
        cost: State.() -> Double = { 1.0 },
        condition: State.() -> Boolean,
    ) {
        goals.add(Goal(name, description, value, cost, condition))
    }

    internal fun build() = GOAPPlanner(name, actions, goals)
}
