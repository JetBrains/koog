@file:Suppress("MissingKDocForPublicAPI", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.agent.entity

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.utils.annotations.InternalKoogUtils
import ai.koog.utils.coroutines.asCoroutineContext
import ai.koog.utils.coroutines.withSuspend
import java.util.concurrent.ExecutorService

@OptIn(InternalKoogUtils::class)
public actual interface AIAgentStrategy<TInput, TOutput, TContext : AIAgentContext> {
    public actual val name: String

    public actual suspend fun execute(context: TContext, input: TInput): TOutput?

    public fun execute(context: TContext, input: TInput, executorService: ExecutorService? = null): TOutput? =
        withSuspend(executorService.asCoroutineContext()) {
            execute(context, input)
        }
}
