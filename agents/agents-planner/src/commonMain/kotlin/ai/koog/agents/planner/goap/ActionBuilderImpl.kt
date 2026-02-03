package ai.koog.agents.planner.goap

internal class ActionBuilderImpl<State> : ActionBuilderApi<State> {
    private var name: String? = null
    private var description: String? = null
    private var precondition: Condition<State>? = null
    private var belief: Belief<State>? = null
    private var cost: Cost<State> = { 1.0 }
    private var execute: Execute<State>? = null

    override fun name(name: String): ActionBuilderImpl<State> = apply { this.name = name }
    override fun description(description: String?): ActionBuilderImpl<State> = apply { this.description = description }
    override fun precondition(precondition: Condition<State>): ActionBuilderImpl<State> =
        apply { this.precondition = precondition }

    override fun belief(belief: Belief<State>): ActionBuilderImpl<State> = apply { this.belief = belief }
    override fun cost(cost: Cost<State>): ActionBuilderImpl<State> = apply { this.cost = cost }
    override fun executeAsync(execute: Execute<State>): ActionBuilderImpl<State> = apply { this.execute = execute }

    override fun build(): Action<State> = Action(
        name = requireNotNull(name) { "Action name is required" },
        description = description,
        precondition = requireNotNull(precondition) { "Action precondition is required" },
        belief = requireNotNull(belief) { "Action belief is required" },
        cost = cost,
        execute = requireNotNull(execute) { "Action execute is required" }
    )
}
