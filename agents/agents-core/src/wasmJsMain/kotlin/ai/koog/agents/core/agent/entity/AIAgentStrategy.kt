@file:Suppress("MissingKDocForPublicAPI", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.agent.entity

import ai.koog.agents.core.agent.context.AIAgentContext

public actual interface AIAgentStrategy<TInput, TOutput, TContext : AIAgentContext> {
    public actual val name: String

    public actual suspend fun execute(context: TContext, input: TInput): TOutput?
}
