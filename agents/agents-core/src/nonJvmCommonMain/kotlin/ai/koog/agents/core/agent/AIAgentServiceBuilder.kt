@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "MissingKDocForPublicAPI")

package ai.koog.agents.core.agent

public actual class AIAgentServiceBuilder internal actual constructor() :
    AIAgentServiceBuilderCommon<AIAgentServiceBuilder>() {
    actual override fun self(): AIAgentServiceBuilder = this
}
