@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.planner.goap

/**
 * Builder for [Action] instances.
 */
public expect class ActionBuilder<State>() : ActionBuilderApi<State> {
    public override fun name(name: String): ActionBuilder<State>
    public override fun description(description: String?): ActionBuilder<State>
    public override fun precondition(precondition: Condition<State>): ActionBuilder<State>
    public override fun belief(belief: Belief<State>): ActionBuilder<State>
    public override fun cost(cost: Cost<State>): ActionBuilder<State>
    public override fun executeAsync(execute: Execute<State>): ActionBuilder<State>
    public override fun build(): Action<State>
}
