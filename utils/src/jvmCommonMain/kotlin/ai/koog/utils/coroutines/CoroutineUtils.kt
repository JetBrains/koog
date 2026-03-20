package ai.koog.utils.coroutines

import ai.koog.utils.annotations.InternalKoogUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@InternalKoogUtils
public fun ExecutorService?.asCoroutineContext(
    defaultExecutorService: ExecutorService? = null,
    fallbackDispatcher: CoroutineDispatcher = Dispatchers.Default
): CoroutineContext =
    (this ?: defaultExecutorService)?.asCoroutineDispatcher() ?: fallbackDispatcher

/**
 * Executes the given suspending block of code on the IO dispatcher.
 */
@InternalKoogUtils
public fun <T> runOnIOBoundDispatcher(
    executorService: ExecutorService? = null,
    block: suspend () -> T
): T =
    withSuspend(
        executorService.asCoroutineContext(
            fallbackDispatcher = Dispatchers.IO
        )
    ) {
        block()
    }

/**
 * A [ThreadLocal] storage for the current [CoroutineContext].
 *
 * This element is used to bridge the gap between suspending Kotlin code and blocking Java/non-suspendable code.
 * It allows [withSuspend] to detect if the current thread is already executing within a coroutine
 * context and which dispatcher is being used.
 *
 * This is critical for:
 * 1. **Re-entrancy Detection**: Identifying when a blocking call from Java has re-entered the agent system.
 * 2. **Deadlock Prevention**: Ensuring that we don't attempt to synchronously dispatch to a dispatcher
 *    that is already blocking the current thread.
 *
 * @see withSuspend
 */
internal val CURRENT_CONTEXT_ELEMENT: ThreadLocal<CoroutineContext> = ThreadLocal()

/**
 * Blocking -> suspend bridge, to call suspendable functions from blocking Java API while avoiding coroutine deadlocks
 * regular runBlocking might cause.
 *
 * Executes a suspending [block] by either using [runBlocking] or immediately executing it if already
 * on the target dispatcher.
 *
 * This function handles the "bridge" between the non-suspending Java API and the suspending internal logic.
 * It uses [CURRENT_CONTEXT_ELEMENT] to track the execution state across blocking boundaries.
 *
 * ### Deadlock Prevention Logic:
 * If the current thread is already associated with a [CoroutineContext] (stored in [CURRENT_CONTEXT_ELEMENT]):
 * 1. It compares the targetDispatcher with the existingDispatcher.
 * 2. If they match (or targetDispatcher is null), it uses `runBlocking(EmptyCoroutineContext)`.
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
@JvmOverloads
@InternalKoogUtils
public fun <T> withSuspend(
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend () -> T
): T {
    val existingContext = CURRENT_CONTEXT_ELEMENT.get()

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
        val old = CURRENT_CONTEXT_ELEMENT.get()
        CURRENT_CONTEXT_ELEMENT.set(coroutineContext)
        try {
            block()
        } finally {
            CURRENT_CONTEXT_ELEMENT.set(old)
        }
    }
}

/**
 * Suspend -> blocking bridge, to call blocking Java API functions from suspendable code while avoiding blocking the
 * coroutine by delegating the [block] to the provided [executor] and waiting for its completion.
 *
 * @param executor The executor to use for executing the [block]. Defaults to [Dispatchers.Default].
 * @param block The blocking lambda to execute.
 */
@InternalKoogUtils
@JvmOverloads
public suspend fun <T> withBlocking(
    executor: Executor = Dispatchers.Default.asExecutor(),
    block: () -> T,
): T {
    val result = CompletableDeferred<T>()

    executor.execute {
        try {
            result.complete(block())
        } catch (e: Throwable) {
            result.completeExceptionally(e)
        }
    }

    return result.await()
}
