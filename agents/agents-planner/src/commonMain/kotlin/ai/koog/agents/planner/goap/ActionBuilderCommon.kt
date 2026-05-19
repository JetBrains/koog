package ai.koog.agents.planner.goap

import kotlin.jvm.JvmSynthetic

/**
 * Action builder configuration common to all platforms.
 */
public abstract class ActionBuilderCommon<State, Self : ActionBuilderCommon<State, Self>> internal constructor() {
    protected abstract fun self(): Self

    private var name: String? = null
    private var description: String? = null
    private var precondition: Condition<State>? = null
    private var belief: Belief<State>? = null
    private var cost: Cost<State> = { 1.0 }
    private var execute: Execute<State>? = null

    /**
     * Sets the name of the action.
     */
    public fun name(name: String): Self = self().apply { this.name = name }

    /**
     * Sets the description of the action.
     */
    public fun description(description: String?): Self = self().apply { this.description = description }

    /**
     * Sets the precondition for the action.
     */
    public fun precondition(precondition: Condition<State>): Self = self().apply { this.precondition = precondition }

    /**
     * Sets the belief for the action.
     */
    public fun belief(belief: Belief<State>): Self = self().apply { this.belief = belief }

    /**
     * Sets the cost function for the action.
     */
    public fun cost(cost: Cost<State>): Self = self().apply { this.cost = cost }

    /**
     * Sets the execute function for the action.
     */
    @JvmSynthetic
    public fun execute(execute: Execute<State>): Self = self().apply { this.execute = execute }

    /**
     * Builds the [Action].
     */
    public fun build(): Action<State> = Action(
        name = requireNotNull(name) { "Action name is required" },
        description = description,
        precondition = requireNotNull(precondition) { "Action precondition is required" },
        belief = requireNotNull(belief) { "Action belief is required" },
        cost = cost,
        execute = requireNotNull(execute) { "Action execute is required" }
    )
}
