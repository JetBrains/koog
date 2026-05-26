package ai.koog.agents.planner.goap

import ai.koog.agents.core.planner.AIAgentPlannerStrategy
import ai.koog.agents.core.utils.ConfigureAction
import ai.koog.serialization.TypeToken
import kotlin.jvm.JvmOverloads
import kotlin.math.exp

/**
 * Builder for GOAP-based [AIAgentPlannerStrategy].
 *
 * Obtain via [ai.koog.agents.planner.Planners.goap].
 */
public class GOAPPlannerBuilder<Input, Output, State : GoapAgentState<Input, Output>>(
    private val name: String,
    private val initializeState: (Input) -> State
) {
    private val actions: MutableList<Action<State>> = mutableListOf()
    private val goals: MutableList<Goal<State>> = mutableListOf()

    private var stateType: TypeToken? = null

    /**
     * Adds an action to the planner.
     */
    public fun action(action: Action<State>): GOAPPlannerBuilder<Input, Output, State> = apply { actions.add(action) }

    /**
     * Adds an action to the planner with the provided configuration lambda.
     */
    public fun action(
        name: String,
        configure: ConfigureAction<ActionBuilder<State>>
    ): GOAPPlannerBuilder<Input, Output, State> = apply {
        action(ActionBuilder<State>().apply { name(name) }.apply(configure::configure).build())
    }

    /**
     * Adds an action to the planner with provided parameters.
     */
    @JvmOverloads
    public fun action(
        name: String,
        description: String? = null,
        precondition: Condition<State>,
        belief: Belief<State>,
        cost: Cost<State> = { 1.0 },
        execute: Execute<State>,
    ): GOAPPlannerBuilder<Input, Output, State> = apply {
        action(Action(name, description, precondition, belief, cost, execute))
    }

    /**
     * Adds a goal to the planner.
     */
    public fun goal(goal: Goal<State>): GOAPPlannerBuilder<Input, Output, State> = apply { goals.add(goal) }

    /**
     * Adds a goal to the planner with the provided configuration lambda.
     */
    public fun goal(
        name: String,
        configure: ConfigureAction<GoalBuilder<State>>
    ): GOAPPlannerBuilder<Input, Output, State> = apply {
        goal(GoalBuilder<State>().apply { name(name) }.apply(configure::configure).build())
    }

    /**
     * Adds a goal to the planner with provided parameters.
     */
    public fun goal(
        name: String,
        description: String? = null,
        value: (Double) -> Double = { cost -> exp(-cost) },
        cost: Cost<State> = { 1.0 },
        condition: Condition<State>,
    ): GOAPPlannerBuilder<Input, Output, State> = apply {
        goal(Goal(name, description, value, cost, condition))
    }

    /**
     * Defines the type of the state.
     */
    public fun stateType(typeToken: TypeToken): GOAPPlannerBuilder<Input, Output, State> = apply { stateType = typeToken }

    /**
     * Builds the planner.
     */
    public fun build(): AIAgentPlannerStrategy<Input, Output> = AIAgentPlannerStrategy(
        name = name,
        planner = GOAPPlanner(stateInitializer = initializeState, actions = actions, goals = goals, stateType = stateType),
    )
}
