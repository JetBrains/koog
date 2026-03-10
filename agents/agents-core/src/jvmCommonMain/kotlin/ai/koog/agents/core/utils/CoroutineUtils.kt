@file:Suppress("MissingKDocForPublicAPI")
package ai.koog.agents.core.utils

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.annotation.InternalAgentsApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ExecutorService
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

internal fun ExecutorService?.asCoroutineContext(
    defaultExecutorService: ExecutorService? = null,
    fallbackDispatcher: CoroutineDispatcher = Dispatchers.Default
): CoroutineContext =
    (this ?: defaultExecutorService)?.asCoroutineDispatcher() ?: fallbackDispatcher

/**
 * Executes the given suspending block of code on the LLM dispatcher (suitable for IO / LLM communication)
 * derived from the provided [executorService], or falls back to [Dispatchers.IO] if none is supplied.
 *
 * @param T The type of the result produced by the suspending [block].
 * @param executorService The custom [ExecutorService] to adapt as a coroutine context. If null, uses the default LLM executor service.
 * @param block The suspending block of code to execute within the resolved coroutine context.
 * @return The result of the executed suspending [block].
 */
@InternalAgentsApi
public fun <T> AIAgentConfig.runOnLLMDispatcher(executorService: ExecutorService? = null, block: suspend () -> T): T {
    val context = executorService.asCoroutineContext(
        defaultExecutorService = llmRequestExecutorService,
        fallbackDispatcher = Dispatchers.IO
    )
    return runBlockingIfRequired(context, block)
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
@InternalAgentsApi
public fun <T> AIAgentConfig.runOnStrategyDispatcher(
    executorService: ExecutorService? = null,
    block: suspend () -> T
): T {
    val context = executorService.asCoroutineContext(
        defaultExecutorService = strategyExecutorService,
        fallbackDispatcher = Dispatchers.Default
    )
    return runBlockingIfRequired(context, block)
}

/**
 * A [ThreadLocal] storage for the current [CoroutineContext].
 *
 * This element is used to bridge the gap between suspending Kotlin code and blocking Java/non-suspendable code.
 * It allows [runBlockingIfRequired] to detect if the current thread is already executing within a coroutine
 * context and which dispatcher is being used.
 *
 * This is critical for:
 * 1. **Re-entrancy Detection**: Identifying when a blocking call from Java has re-entered the agent system.
 * 2. **Deadlock Prevention**: Ensuring that we don't attempt to synchronously dispatch to a dispatcher
 *    that is already blocking the current thread.
 *
 * @see runBlockingIfRequired
 */
internal val AGENT_CONTEXT_ELEMENT: ThreadLocal<CoroutineContext> = ThreadLocal()

/**
 * Executes a suspending [block] by either using [runBlocking] or immediately executing it if already
 * on the target dispatcher.
 *
 * This function handles the "bridge" between the non-suspending Java API and the suspending internal logic.
 * It uses [AGENT_CONTEXT_ELEMENT] to track the execution state across blocking boundaries.
 *
 * ### Deadlock Prevention Logic:
 * If the current thread is already associated with a [CoroutineContext] (stored in [AGENT_CONTEXT_ELEMENT]):
 * 1. It compares the [targetDispatcher] with the [existingDispatcher].
 * 2. If they match (or [targetDispatcher] is null), it uses `runBlocking(EmptyCoroutineContext)`.
 *    This starts a nested event loop on the **current thread** without trying to reschedule the task
 *    on the dispatcher's executor service. This is vital because the executor might be single-threaded
 *    and currently blocked by the outer `runBlocking` call.
 *
 * If the dispatchers differ, it performs a standard `runBlocking(context)`, which may block the current
 * thread while the block executes on a different thread pool (e.g., switching from Strategy to LLM pool).
 *
 * @param context The coroutine context to use for execution. Defaults to [EmptyCoroutineContext].
 * @param block The suspending block to execute.
 * @return The result of the [block].
 */
@OptIn(InternalAgentsApi::class)
@InternalAgentsApi
public fun <T> runBlockingIfRequired(context: CoroutineContext = EmptyCoroutineContext, block: suspend () -> T): T {
    val existingContext = AGENT_CONTEXT_ELEMENT.get()

    if (existingContext != null) {
        val targetDispatcher = context[ContinuationInterceptor] as? CoroutineDispatcher
        val existingDispatcher = existingContext[ContinuationInterceptor] as? CoroutineDispatcher

        if (targetDispatcher == null || targetDispatcher == existingDispatcher) {
            // We are already on the same dispatcher.
            // Using a new runBlocking with EmptyCoroutineContext will block the current thread
            // but won't try to dispatch to the executor again, avoiding deadlock.
            return runBlocking(EmptyCoroutineContext) {
                block()
            }
        }
    }

    return runBlocking(context) {
        val old = AGENT_CONTEXT_ELEMENT.get()
        AGENT_CONTEXT_ELEMENT.set(coroutineContext)
        try {
            block()
        } finally {
            AGENT_CONTEXT_ELEMENT.set(old)
        }
    }
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
