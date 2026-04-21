package ai.koog.agents.planner.goap

import ai.koog.agents.core.utils.ConfigureAction
import ai.koog.serialization.TypeToken
import kotlin.jvm.JvmOverloads
import kotlin.math.exp

/**
 * [GOAPPlanner] DSL builder.
 */
public open class GOAPPlannerBuilder<State : GoapAgentState<*, *>> @JvmOverloads constructor(
    private val stateType: TypeToken? = null,
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
     * Defines and configures an action available to the GOAP agent using the provided builder.
     *
     * @param name The name of the action.
     * @param configure A configuration function used to customize the action builder.
     */
    public fun action(
        name: String,
        configure: ConfigureAction<ActionBuilder<State>>
    ): GOAPPlannerBuilder<State> = apply {
        action(ActionBuilder<State>().apply { name(name) }.apply(configure::configure).build())
    }

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
    @JvmOverloads
    public fun action(
        name: String,
        description: String? = null,
        precondition: Condition<State>,
        belief: Belief<State>,
        cost: Cost<State> = { 1.0 },
        execute: Execute<State>,
    ): GOAPPlannerBuilder<State> = apply {
        action(Action(name, description, precondition, belief, cost, execute))
    }

    /**
     * Defines a goal for the GOAP agent.
     *
     * Returns the builder instance for chained calls.
     */
    public fun goal(goal: Goal<State>): GOAPPlannerBuilder<State> = apply { goals.add(goal) }

    /**
     * Defines and configures a goal for the GOAP agent using the provided builder.
     *
     * @param name The name of the goal.
     * @param configure A configuration function used to customize the goal builder.
     */
    public fun goal(
        name: String,
        configure: ConfigureAction<GoalBuilder<State>>
    ): GOAPPlannerBuilder<State> = apply {
        goal(GoalBuilder<State>().apply { name(name) }.apply(configure::configure).build())
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
        cost: Cost<State> = { 1.0 },
        condition: Condition<State>,
    ): GOAPPlannerBuilder<State> = apply {
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
 * @param stateType [TypeToken] of the [State].
 * @param init The initialization block for the builder.
 * @return A new [GOAPPlanner] instance with the defined actions.
 */
@Deprecated("Use AIAgentStrategy.builder(name).goap() DSL instead.")
public fun <State : GoapAgentState<*, *>> goap(
    stateType: TypeToken,
    init: GOAPPlannerBuilder<State>.() -> Unit
): GOAPPlanner<State> {
    val builder = GOAPPlannerBuilder<State>(stateType)
    builder.init()
    return builder.build()
}
