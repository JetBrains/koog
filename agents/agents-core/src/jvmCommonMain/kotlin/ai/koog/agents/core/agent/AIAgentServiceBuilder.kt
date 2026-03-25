@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "MissingKDocForPublicAPI")

package ai.koog.agents.core.agent

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import java.util.function.BiFunction

public actual class AIAgentServiceBuilder internal actual constructor() :
    AIAgentServiceBuilderCommon<AIAgentServiceBuilder>() {
    actual override fun self(): AIAgentServiceBuilder = this

    /**
     * Creates a functional agent service builder using the provided strategy.
     *
     * This method allows defining a custom functional strategy for the AI agent.
     *
     * @param Input The type of the input parameter for the strategy's execution logic.
     * @param Output The type of the output returned by the strategy's execution logic.
     * @param name The name identifying the functional strategy. Defaults to "funStrategy".
     * @param strategy The implementation of the functional strategy's execution logic.
     * @return A `FunctionalAgentBuilder` configured with the specified functional strategy.
     */
    @JavaAPI
    @JvmOverloads
    public fun <Input, Output> functionalStrategy(
        name: String = "funStrategy",
        strategy: BiFunction<AIAgentFunctionalContext, Input, Output>
    ): FunctionalAgentServiceBuilder<Input, Output> = functionalStrategy(
        object : NonSuspendAIAgentFunctionalStrategy<Input, Output>(name) {
            override fun executeStrategy(context: AIAgentFunctionalContext, input: Input): Output =
                strategy.apply(context, input)
        }
    )
}
