@file:Suppress("MissingKDocForPublicAPI")

package ai.koog.agents.core.utils

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.annotation.InternalAgentsApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
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
 * Executes a suspending [block] as a bridge from blocking (Java) code into the coroutine world.
 * It uses [AGENT_CONTEXT_ELEMENT] to track the current logical dispatcher across blocking boundaries
 * and avoid deadlocks in two scenarios:
 *
 * **Re-entrant call (AGENT_CONTEXT_ELEMENT is set):** the current thread is already inside a
 * `runBlocking` started by this function. If the target dispatcher matches the existing one,
 * using `runBlocking(context)` would try to re-dispatch onto the same executor — which may be
 * single-threaded and already blocked. `runBlocking(EmptyCoroutineContext)` avoids the re-dispatch
 * by running inline on the current thread's event loop instead.
 *
 * **First entry (AGENT_CONTEXT_ELEMENT is null):** the calling thread may itself be a worker of
 * the target executor (e.g. `agent.run()` called from inside `executor.submit()`). In that case,
 * `runBlocking(context)` would dispatch the coroutine onto the executor and then park the calling
 * thread waiting for it — but the calling thread IS the only worker, so the coroutine never runs.
 * `runBlocking(EmptyCoroutineContext)` runs the coroutine inline on the current thread, bypassing
 * the executor queue entirely. The TARGET context is stored in [AGENT_CONTEXT_ELEMENT] (not the
 * actual `coroutineContext`) so that nested calls can still compare dispatchers correctly.
 *
 * @param context The coroutine context to use for execution. Defaults to [EmptyCoroutineContext].
 * @param block The suspending block to execute.
 * @return The result of the [block].
 */
@OptIn(InternalAgentsApi::class)
@JvmOverloads
@InternalAgentsApi
public fun <T> runBlockingIfRequired(context: CoroutineContext = EmptyCoroutineContext, block: suspend () -> T): T {
    val existingContext = AGENT_CONTEXT_ELEMENT.get()

    if (existingContext != null) {
        val targetDispatcher = context[ContinuationInterceptor] as? CoroutineDispatcher
        val existingDispatcher = existingContext[ContinuationInterceptor] as? CoroutineDispatcher

        if (targetDispatcher == null || targetDispatcher == existingDispatcher) {
            // Same dispatcher: running runBlocking(context) would re-dispatch onto the same
            // executor that is already blocked waiting for us — deadlock. Run inline instead.
            return runBlocking(EmptyCoroutineContext) {
                block()
            }
        }
    }

    // First entry from non-coroutine code (AGENT_CONTEXT_ELEMENT == null).
    // runBlocking(context) would dispatch the coroutine onto the executor and park this thread.
    //
    // If this thread is itself a worker of that executor, the coroutine would sit in the queue
    // with no thread available to run it - deadlock.
    //
    // runBlocking(EmptyCoroutineContext) runs the coroutine inline on the current thread, so
    // the executor queue is never involved.
    //
    // Note: we store the TARGET context (not coroutineContext) in AGENT_CONTEXT_ELEMENT so that
    // nested calls compare against the intended dispatcher, not the BlockingEventLoop.
    return runBlocking(EmptyCoroutineContext) {
        val old = AGENT_CONTEXT_ELEMENT.get()
        AGENT_CONTEXT_ELEMENT.set(context)
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
    // If the current thread is a worker of strategyExecutorService and is blocked inside
    // runBlocking's event loop, it cannot drain the executor's task queue. Submitting block()
    // via executor.execute would queue it there and suspend waiting, but nobody can pick it.
    // Detect this case and run block() directly on the current thread instead.
    val existingContext = AGENT_CONTEXT_ELEMENT.get()
    if (existingContext != null) {
        val existingExecutor = (existingContext[ContinuationInterceptor] as? ExecutorCoroutineDispatcher)?.executor
        if (existingExecutor != null && existingExecutor === strategyExecutorService) {
            return block()
        }
    }

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
