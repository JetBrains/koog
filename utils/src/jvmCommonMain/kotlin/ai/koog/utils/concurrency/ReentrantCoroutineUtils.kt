package ai.koog.utils.concurrency

import ai.koog.utils.annotations.InternalKoogUtils
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Runs a suspending [block] from blocking code in a reentrant-safe way.
 *
 * The caller thread is blocked until [block] completes.
 * The block normally runs with [context], but if the current execution is already associated with the same continuation
 * interceptor (dispatcher), it strips the interceptor from the context to avoid redispatching.
 * This avoids redispatch deadlocks when all threads in the target context are busy.
 *
 * @param context The coroutine context in which the block should be executed. Defaults to [EmptyCoroutineContext].
 * @param block The suspend block of code to be executed.
 */
@InternalKoogUtils
@JvmOverloads
public fun <T> runBlockingReentrant(
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend () -> T,
): T = runBlocking {
    withContextReentrant(context, block)
}

/**
 * Runs the given [block] within the specified [context] in a reentrant-safe way.
 *
 * The block normally runs with [context], but if the current execution is already associated with the same continuation
 * interceptor (dispatcher), it strips the interceptor from the context to avoid redispatching.
 * This avoids redispatch deadlocks when all threads in the target context are busy.
 *
 * @param context The target coroutine context in which the block should execute.
 * @param block The suspend function to be executed within the specified context.
 */
@InternalKoogUtils
public suspend fun <T> withContextReentrant(
    context: CoroutineContext,
    block: suspend () -> T
): T {
    val currentContinuationInterceptor = CURRENT_CONTINUATION_INTERCEPTOR.get()
    val targetContinuationInterceptor = context[ContinuationInterceptor]

    val actualContext = if (currentContinuationInterceptor == targetContinuationInterceptor) {
        // Already using the same existing interceptor, strip it from the context to avoid redispatch deadlocks
        context.minusKey(ContinuationInterceptor)
    } else {
        // Need to execute on a new interceptor, preserve it in a thread local and dispatch
        context + CURRENT_CONTINUATION_INTERCEPTOR.asContextElement(targetContinuationInterceptor)
    }

    return withContext(actualContext) {
        block()
    }
}

/**
 * A [ThreadLocal] storage for the current [ContinuationInterceptor].
 *
 * This element is used to bridge the gap between suspending Kotlin code and blocking Java/non-suspendable code.
 * It allows [withContextReentrant] to detect if the current thread is already executing within a coroutine
 * context and which dispatcher (continuation interceptor) is being used.
 *
 * This is critical for:
 * 1. **Re-entrancy Detection**: Identifying when a blocking call from Java has re-entered the agent system.
 * 2. **Deadlock Prevention**: Ensuring that we don't attempt to synchronously dispatch to a dispatcher
 *    that is already blocking the current thread.
 */
private val CURRENT_CONTINUATION_INTERCEPTOR: ThreadLocal<ContinuationInterceptor?> = ThreadLocal()
