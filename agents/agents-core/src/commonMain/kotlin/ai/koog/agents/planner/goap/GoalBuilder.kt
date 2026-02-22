package ai.koog.agents.planner.goap

/**
 * Builder for [Goal] instances.
 */
public class GoalBuilder<State> {
    private var name: String? = null
    private var description: String? = null
    private var value: (Double) -> Double = { 1 / it }
    private var cost: Cost<State> = { 1.0 }
    private var condition: Condition<State>? = null

    /**
     * Sets the name of the goal.
     */
    public fun name(name: String): GoalBuilder<State> = apply { this.name = name }

    /**
     * Sets the description of the goal.
     */
    public fun description(description: String?): GoalBuilder<State> = apply { this.description = description }

    /**
     * Sets the value function for the goal.
     */
    public fun value(value: (Double) -> Double): GoalBuilder<State> = apply { this.value = value }

    /**
     * Sets the cost function for the goal.
     */
    public fun cost(cost: Cost<State>): GoalBuilder<State> = apply { this.cost = cost }

    /**
     * Sets the condition function for the goal.
     */
    public fun condition(condition: Condition<State>): GoalBuilder<State> = apply { this.condition = condition }

    /**
     * Creates an instance of [Goal] based on the builder's configuration.
     */
    public fun build(): Goal<State> = Goal(
        name = requireNotNull(name) { "Goal name is required" },
        description = description,
        value = value,
        cost = cost,
        condition = requireNotNull(condition) { "Goal condition is required" }
    )
}
