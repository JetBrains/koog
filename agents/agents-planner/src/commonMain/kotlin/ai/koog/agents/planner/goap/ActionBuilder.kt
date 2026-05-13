@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.planner.goap

/**
 * Builder for [Action] instances.
 */
public expect class ActionBuilder<State>() : ActionBuilderCommon<State, ActionBuilder<State>> {
    override fun self(): ActionBuilder<State>
}
