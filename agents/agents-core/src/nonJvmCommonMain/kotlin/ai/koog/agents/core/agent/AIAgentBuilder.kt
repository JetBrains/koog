@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "MissingKDocForPublicAPI")

package ai.koog.agents.core.agent

public actual class AIAgentBuilder internal actual constructor() : AIAgentBuilderCommon<AIAgentBuilder>() {
    actual override fun self(): AIAgentBuilder = this
}
