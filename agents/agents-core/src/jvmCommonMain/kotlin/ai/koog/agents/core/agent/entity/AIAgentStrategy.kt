@file:Suppress("MissingKDocForPublicAPI", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(InternalKoogUtils::class)

package ai.koog.agents.core.agent.entity

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.utils.runBlockingOnStrategyDispatcher
import ai.koog.utils.annotations.InternalKoogUtils
import java.util.concurrent.ExecutorService

public actual interface AIAgentStrategy<TInput, TOutput, TContext : AIAgentContext> {
    public actual val name: String

    public actual suspend fun execute(context: TContext, input: TInput): TOutput?

    @OptIn(InternalAgentsApi::class)
    public fun execute(
        context: TContext,
        input: TInput,
        executorService: ExecutorService? = null,
    ): TOutput? = context.config.runBlockingOnStrategyDispatcher(executorService) {
        execute(context, input)
    }
}
