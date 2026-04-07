@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "MissingKDocForPublicAPI")

package ai.koog.agents.core.agent

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.GraphStrategyBuilder
import ai.koog.agents.core.utils.BuilderChainAction
import java.util.function.BiFunction

public actual class AIAgentBuilder internal actual constructor() : AIAgentBuilderCommon<AIAgentBuilder>() {
    actual override fun self(): AIAgentBuilder = this

    /**
     * Creates a functional agent builder using the provided strategy configuration.
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
    ): FunctionalAgentBuilder<Input, Output> = functionalStrategy(
        object : NonSuspendAIAgentFunctionalStrategy<Input, Output>(name) {
            override fun executeStrategy(context: AIAgentFunctionalContext, input: Input): Output =
                strategy.apply(context, input)
        }
    )

    /**
     * Creates a graph agent builder using the provided strategy configuration.
     *
     * This method allows defining a custom graph strategy for the AI agent.
     *
     * @param Input The type of the input parameter for the strategy's execution logic.
     * @param Output The type of the output returned by the strategy's execution logic.
     * @param name The name identifying the functional strategy. Defaults to "funStrategy".
     * @param build The configuration of the graph strategy.
     * @return A `GraphAgentBuilder` configured with the specified graph strategy.
     */
    @JavaAPI
    @JvmOverloads
    public fun <Input, Output> graphStrategy(
        name: String = "graphStrategy",
        build: BuilderChainAction<GraphStrategyBuilder, AIAgentGraphStrategy<Input, Output>>
    ): GraphAgentBuilder<Input, Output> = graphStrategy(
        build.configure(
            GraphStrategyBuilder(strategyName = name)
        )
    )
}
