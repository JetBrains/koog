@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "MissingKDocForPublicAPI")

package ai.koog.agents.planner.goap

import ai.koog.agents.core.annotation.InternalAgentsApi

@OptIn(InternalAgentsApi::class)
public actual class ActionBuilder<State> : ActionBuilderCommon<State, ActionBuilder<State>>() {
    public actual override fun self(): ActionBuilder<State> = this
}
