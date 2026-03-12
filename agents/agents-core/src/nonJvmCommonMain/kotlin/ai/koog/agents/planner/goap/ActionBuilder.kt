@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.planner.goap

import ai.koog.agents.core.annotation.InternalAgentsApi

@OptIn(InternalAgentsApi::class)
public actual class ActionBuilder<State> : ActionBuilderApi<State> {
    private val delegate = ActionBuilderImpl<State>()
    public actual override fun name(name: String): ActionBuilder<State> = apply { delegate.name(name) }
    public actual override fun description(description: String?): ActionBuilder<State> =
        apply { delegate.description(description) }
    public actual override fun precondition(precondition: Condition<State>): ActionBuilder<State> =
        apply { delegate.precondition(precondition) }
    public actual override fun belief(belief: Belief<State>): ActionBuilder<State> = apply { delegate.belief(belief) }
    public actual override fun cost(cost: Cost<State>): ActionBuilder<State> = apply { delegate.cost(cost) }
    public actual override fun execute(execute: Execute<State>): ActionBuilder<State> =
        apply { delegate.execute(execute) }
    public actual override fun build(): Action<State> = delegate.build()
}
