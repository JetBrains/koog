package ai.koog.test.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.awaitility.core.ConditionFactory
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.ContinuationInterceptor
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
 * Repeatedly evaluates the given [block] until it does not throw any exceptions.
 *
 * This method is particularly helpful in cases where conditions are expected to eventually
 * become true due to the nature of concurrent or delayed behavior.
 *
 * This version allows providing a [scope] to inherit the coroutine context. It automatically
 * removes the [ContinuationInterceptor] to prevent deadlocks when used within `runTest`.
 *
 * @param scope The [CoroutineScope] used to provide the context for the suspendable block.
 * @param block A suspendable lambda function containing the assertions or conditions to be verified.
 * @return The result of the [block] when it successfully completes without throwing an exception.
 */
@OptIn(ExperimentalAtomicApi::class)
public fun <T> ConditionFactory.untilAsserted(scope: CoroutineScope, block: suspend () -> T?): T? {
    val result = AtomicReference<T?>(null)
    untilAsserted {
        result.store(
            runBlocking(scope.coroutineContext.minusKey(ContinuationInterceptor)) {
                block()
            }
        )
    }
    return result.load()
}

/**
 * Repeatedly evaluates the given [block] until it does not throw any exceptions.
 *
 * This method is useful for asserting conditions that may eventually become true,
 * particularly in scenarios involving asynchronous or concurrent operations.
 *
 * @param block A suspendable lambda function containing the assertions or conditions to be verified.
 * @return The result of the [block] when it successfully completes without throwing an exception.
 */
@OptIn(ExperimentalAtomicApi::class)
public fun <T> ConditionFactory.untilAsserted(block: suspend () -> T?): T? {
    val result = AtomicReference<T?>(null)
    untilAsserted {
        result.store(
            runBlocking {
                block()
            }
        )
    }
    return result.load()
}

/**
 * Sets the maximum duration to wait for the condition to be satisfied.
 *
 * @param duration The maximum duration as a Kotlin [Duration].
 * @return The updated [ConditionFactory] instance.
 */
public fun ConditionFactory.atMost(duration: Duration): ConditionFactory =
    this.atMost(duration.toJavaDuration())

/**
 * Specifies the minimum amount of time that the condition should be evaluated for.
 *
 * @param duration The minimum duration to evaluate the condition as a Kotlin [Duration].
 * @return The updated [ConditionFactory] instance.
 */
public fun ConditionFactory.atLeast(duration: Duration): ConditionFactory =
    this.atLeast(duration.toJavaDuration())

/**
 * Specifies the delay before polling begins when evaluating a condition.
 *
 * @param duration The duration to wait before the first polling attempt as a Kotlin [Duration].
 * @return The updated [ConditionFactory] instance.
 */
public fun ConditionFactory.pollDelay(duration: Duration): ConditionFactory =
    this.pollDelay(duration.toJavaDuration())

/**
 * Specifies the interval between consecutive polling attempts when evaluating a condition.
 *
 * @param duration The interval duration between polling attempts as a Kotlin [Duration].
 * @return The updated [ConditionFactory] instance.
 */
public fun ConditionFactory.pollInterval(duration: Duration): ConditionFactory =
    this.pollInterval(duration.toJavaDuration())
