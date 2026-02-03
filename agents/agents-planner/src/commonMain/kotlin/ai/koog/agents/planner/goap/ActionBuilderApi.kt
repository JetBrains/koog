package ai.koog.agents.planner.goap

/**
 * API for building [Action] instances.
 */
public interface ActionBuilderApi<State> {
    /**
     * Sets the name of the action.
     */
    public fun name(name: String): ActionBuilderApi<State>

    /**
     * Sets the description of the action.
     */
    public fun description(description: String?): ActionBuilderApi<State>

    /**
     * Sets the precondition for the action.
     */
    public fun precondition(precondition: Condition<State>): ActionBuilderApi<State>

    /**
     * Sets the belief for the action.
     */
    public fun belief(belief: Belief<State>): ActionBuilderApi<State>

    /**
     * Sets the cost function for the action.
     */
    public fun cost(cost: Cost<State>): ActionBuilderApi<State>

    /**
     * Sets the execute function for the action.
     */
    public fun executeAsync(execute: Execute<State>): ActionBuilderApi<State>

    /**
     * Builds the [Action].
     */
    public fun build(): Action<State>
}
