package ai.koog.agents.planner.goap

import kotlin.jvm.JvmOverloads
import kotlin.math.exp
import kotlin.reflect.KType

/**
 * [GOAPPlanner] DSL builder.
 */
public class GOAPPlannerBuilder<State> @JvmOverloads constructor(
    private val stateType: KType? = null,
) {
    private val actions: MutableList<Action<State>> = mutableListOf()
    private val goals: MutableList<Goal<State>> = mutableListOf()

    /**
     * Defines an action available to the GOAP agent.
     *
     * Returns the builder instance for chained calls.
     */
    public fun action(action: Action<State>): GOAPPlannerBuilder<State> = apply { actions.add(action) }

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
        precondition: Condition<State>,
        belief: Belief<State>,
        cost: Cost<State> = { 1.0 },
        execute: Execute<State>,
    ) {
        action(Action(name, description, precondition, belief, cost, execute))
    }

    /**
     * Defines a goal for the GOAP agent.
     *
     * Returns the builder instance for chained calls.
     */
    public fun goal(goal: Goal<State>): GOAPPlannerBuilder<State> = apply { goals.add(goal) }

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
        cost: Cost<State> = { 1.0 },
        condition: Condition<State>,
    ) {
        goal(Goal(name, description, value, cost, condition))
    }

    /**
     * Builds the [GOAPPlanner].
     */
    public fun build(): GOAPPlanner<State> = GOAPPlanner(actions, goals, stateType)
}

/**
 * Creates a [GOAPPlanner] using a DSL for defining actions.
 *
 * @param stateType [KType] of the [State].
 * @param init The initialization block for the builder.
 * @return A new [GOAPPlanner] instance with the defined actions.
 */
public fun <State> goap(
    stateType: KType,
    init: GOAPPlannerBuilder<State>.() -> Unit
): GOAPPlanner<State> {
    val builder = GOAPPlannerBuilder<State>(stateType)
    builder.init()
    return builder.build()
}
