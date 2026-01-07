package ai.koog.test.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.awaitility.core.ConditionFactory
import kotlin.coroutines.ContinuationInterceptor
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
 * Repeatedly evaluates the given block until it does not throw any exceptions,
 * providing support for asynchronous or coroutine-based operations. This method
 * is particularly helpful in cases where conditions are expected to eventually
 * become true due to the nature of concurrent or delayed behavior.
 *
 * @param scope The [CoroutineScope] used to provide the context for the suspendable block.
 * @param block A suspendable lambda function containing the assertions or conditions to be verified.
 */
public fun ConditionFactory.untilAsserted(scope: CoroutineScope, block: suspend () -> Unit) {
    val self: ConditionFactory = this
    self.untilAsserted {
        runBlocking(scope.coroutineContext.minusKey(ContinuationInterceptor)) {
            block()
        }
    }
}

/**
 * Repeatedly evaluates the given block until it does not throw any exceptions.
 * This method is useful for asserting conditions that may eventually become true,
 * particularly in scenarios involving asynchronous or concurrent operations.
 *
 * @param block A suspendable lambda function containing the assertions or conditions to be verified.
 */
public fun ConditionFactory.untilAsserted(block: suspend () -> Unit) {
    val self: ConditionFactory = this
    self.untilAsserted {
        runBlocking {
            block()
        }
    }
}

/**
 * Repeatedly evaluates the given block until it does not throw any exceptions.
 * This method is useful for asserting conditions that may eventually become true,
 * particularly in scenarios involving asynchronous or concurrent operations.
 *
 * @param block A suspendable lambda function containing the assertions or conditions to be verified.
 */
public fun ConditionFactory.atMost(duration: Duration): ConditionFactory =
    this.atMost(duration.toJavaDuration())

/**
 * Specifies the minimum amount of time that the condition should be evaluated for.
 *
 * @param duration The minimum duration to evaluate the condition, represented as a [Duration].
 * @return The updated [ConditionFactory] instance with the specified minimum duration applied.
 */
public fun ConditionFactory.atLeast(duration: Duration): ConditionFactory =
    this.atLeast(duration.toJavaDuration())

/**
 * Specifies the delay before polling begins when evaluating a condition.
 *
 * @param duration The duration to wait before the first polling attempt, represented as a [Duration].
 * @return The updated [ConditionFactory] instance with the specified polling delay applied.
 */
public fun ConditionFactory.pollDelay(duration: Duration): ConditionFactory =
    this.pollDelay(duration.toJavaDuration())

/**
 * Specifies the interval between consecutive polling attempts when evaluating a condition.
 *
 * @param duration The interval duration between polling attempts, represented as a [Duration].
 * @return The updated [ConditionFactory] instance with the specified polling interval applied.
 */
public fun ConditionFactory.pollInterval(duration: Duration): ConditionFactory =
    this.pollInterval(duration.toJavaDuration())
