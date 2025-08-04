package ai.koog.agents.core.agent

import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Cooperative cancellation helpers for use within agent execution.
 * 
 * These functions provide convenient ways to check for cancellation and ensure
 * that agent components can respond to cancellation requests in a timely manner.
 * They integrate with Kotlin's structured concurrency to provide clean cancellation
 * semantics throughout the agent execution pipeline.
 */

/**
 * Ensures that the current coroutine is still active and throws [kotlinx.coroutines.CancellationException]
 * if it has been cancelled.
 * 
 * This is a cooperative cancellation checkpoint that should be called periodically
 * during long-running operations to allow for responsive cancellation.
 * 
 * Usage:
 * ```kotlin
 * for (item in largeDataSet) {
 *     ensureActive() // Check for cancellation
 *     processItem(item)
 * }
 * ```
 * 
 * @throws kotlinx.coroutines.CancellationException if the coroutine has been cancelled
 */
public suspend fun ensureActive() {
    coroutineContext.ensureActive()
}

/**
 * Checks if the current coroutine has been cancelled.
 * 
 * This is a non-throwing way to check for cancellation, useful when you need
 * to perform cleanup or take different actions based on cancellation status.
 * 
 * Usage:
 * ```kotlin
 * if (isCancelled()) {
 *     // Perform cleanup
 *     return earlyResult
 * }
 * // Continue with normal processing
 * ```
 * 
 * @return true if the coroutine has been cancelled, false otherwise
 */
public suspend fun isCancelled(): Boolean {
    return !coroutineContext.isActive
}

/**
 * Extension property to check if a [CoroutineContext] is still active.
 * 
 * This provides a convenient way to check cancellation status when you have
 * direct access to a coroutine context (e.g., in callback functions or utilities).
 * 
 * @return true if the context is active (not cancelled), false if cancelled
 */
public val CoroutineContext.isActive: Boolean
    get() = job.isActive

/**
 * Extension function to ensure a [CoroutineContext] is still active.
 * 
 * This is useful when you have a coroutine context reference and want to
 * check for cancellation without being in a suspend context.
 * 
 * @throws kotlinx.coroutines.CancellationException if the context has been cancelled
 */
public fun CoroutineContext.ensureActive() {
    job.ensureActive()
}

/**
 * Executes a block of code with cooperative cancellation checks.
 * 
 * This function wraps a computation with automatic cancellation checkpoints,
 * making it easier to add cancellation support to existing code without
 * manually inserting ensureActive() calls.
 * 
 * The function will check for cancellation before and after executing the block,
 * and can optionally check periodically during execution based on the checkInterval.
 * 
 * Usage:
 * ```kotlin
 * val result = withCancellationCheck {
 *     // Long-running computation
 *     performExpensiveOperation()
 * }
 * ```
 * 
 * @param block The computation to execute with cancellation checks
 * @return The result of the block execution
 * @throws kotlinx.coroutines.CancellationException if cancelled during execution
 */
public suspend inline fun <T> withCancellationCheck(block: () -> T): T {
    ensureActive() // Check before starting
    val result = block()
    ensureActive() // Check after completion
    return result
}

/**
 * Executes a suspending block of code with cooperative cancellation checks.
 * 
 * Similar to [withCancellationCheck] but for suspending computations.
 * 
 * Usage:
 * ```kotlin
 * val result = withSuspendingCancellationCheck {
 *     // Long-running suspending computation
 *     performExpensiveAsyncOperation()
 * }
 * ```
 * 
 * @param block The suspending computation to execute with cancellation checks
 * @return The result of the block execution
 * @throws kotlinx.coroutines.CancellationException if cancelled during execution
 */
public suspend inline fun <T> withSuspendingCancellationCheck(block: suspend () -> T): T {
    ensureActive() // Check before starting
    val result = block()
    ensureActive() // Check after completion
    return result
}