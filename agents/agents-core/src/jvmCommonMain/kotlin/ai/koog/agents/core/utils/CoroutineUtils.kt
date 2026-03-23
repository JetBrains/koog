@file:Suppress("MissingKDocForPublicAPI")

package ai.koog.agents.core.utils

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.utils.annotations.InternalKoogUtils
import ai.koog.utils.coroutines.asCoroutineContext
import ai.koog.utils.coroutines.withSuspend
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import java.util.concurrent.ExecutorService

/**
 * Executes the given suspending block of code on the LLM dispatcher (suitable for IO / LLM communication)
 * derived from the provided [executorService], or falls back to [Dispatchers.IO] if none is supplied.
 *
 * @param T The type of the result produced by the suspending [block].
 * @param executorService The custom [ExecutorService] to adapt as a coroutine context. If null, uses the default LLM executor service.
 * @param block The suspending block of code to execute within the resolved coroutine context.
 * @return The result of the executed suspending [block].
 */
@OptIn(InternalKoogUtils::class)
@InternalAgentsApi
public fun <T> AIAgentConfig.runOnLLMDispatcher(executorService: ExecutorService? = null, block: suspend () -> T): T {
    val context = executorService.asCoroutineContext(
        defaultExecutorService = llmRequestExecutorService,
        fallbackDispatcher = Dispatchers.IO
    )
    return withSuspend(context, block)
}

/**
 * Executes a given suspending block of code within a coroutine context on a strategy dispatcher that is
 * determined by the provided [executorService] . If no [executorService] is
 * supplied, it defaults to the [AIAgentConfig.strategyExecutorService] or falls back to
 * [Dispatchers.Default] if none is configured.
 *
 * @param T The return type of the suspending block.
 * @param executorService The optional `ExecutorService` that determines the
 *        coroutine context. If null, the `strategyExecutorService` or
 *        `Dispatchers.Default` will be used as the fallback.
 * @param block The suspending lambda to be executed in the resolved context.
 * @return The result returned by the suspending block after execution.
 */
@OptIn(InternalKoogUtils::class)
@InternalAgentsApi
public fun <T> AIAgentConfig.runOnStrategyDispatcher(
    executorService: ExecutorService? = null,
    block: suspend () -> T
): T {
    val context = executorService.asCoroutineContext(
        defaultExecutorService = strategyExecutorService,
        fallbackDispatcher = Dispatchers.Default
    )
    return withSuspend(context, block)
}

/**
 * Submits a block of code to the main dispatcher for execution.
 *
 * This method ensures that the given block is executed asynchronously using either
 * [AIAgentConfig.strategyExecutorService] if configured or [Dispatchers.Default] otherwise.
 *
 * @param T The return type of the block to be executed.
 * @param block A lambda function that contains the code to be executed asynchronously.
 * @return The result of the executed block.
 */
@InternalAgentsApi
public suspend fun <T> AIAgentConfig.submitToMainDispatcher(block: () -> T): T {
    val result = CompletableDeferred<T>()

    (strategyExecutorService ?: Dispatchers.Default.asExecutor()).execute {
        try {
            result.complete(block())
        } catch (e: Throwable) {
            result.completeExceptionally(e)
        }
    }

    return result.await()
}
