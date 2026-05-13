@file:Suppress(
    "MissingKDocForPublicAPI",
    "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"
)

package ai.koog.agents.planner.goap

public actual class ActionBuilder<State> : ActionBuilderCommon<State, ActionBuilder<State>>() {
    actual override fun self(): ActionBuilder<State> = this
}
