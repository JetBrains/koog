package ai.koog.agents.core.agent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration

/**
 * Extension functions for AIAgent that provide convenient cancellation and timeout support.
 *
 * These extensions build on the core cancellation infrastructure to provide common
 * use cases like timeout handling, while maintaining the clean tri-state outcome semantics.
 */

/**
 * Executes the agent with a timeout, returning a tri-state outcome.
 *
 * This extension function wraps the agent execution with a timeout, automatically
 * converting timeout cancellations to the appropriate [RunOutcome.Cancelled] with
 * [CancellationReason.Timeout]. This provides clean timeout semantics without
 * needing to catch [TimeoutCancellationException] manually.
 *
 * @param input The input to provide to the agent
 * @param timeout The maximum duration to wait for the agent to complete
 * @return The outcome of the agent execution (Success, Failure, or Cancelled)
 */
public suspend fun <Input, Output> AIAgent<Input, Output>.runWithTimeout(
    input: Input,
    timeout: Duration
): RunOutcome<Output> {
    return try {
        withTimeout(timeout) {
            runCancellable(input)
        }
    } catch (e: TimeoutCancellationException) {
        RunOutcome.Cancelled(
            reason = CancellationReason.Timeout,
            message = "Agent execution timed out after $timeout"
        )
    } catch (e: CancellationException) {
        // Handle other types of cancellation (system, etc.)
        RunOutcome.Cancelled(
            reason = CancellationReason.System,
            message = e.message ?: "Agent execution was cancelled"
        )
    }
}

/**
 * Executes the agent and returns only the value on success, throwing on failure or cancellation.
 *
 * This extension provides a convenient way to get the result when you expect success
 * and want to handle failures and cancellations as exceptions. This is useful when
 * you need the old behavior of just getting the result or throwing.
 *
 * @param input The input to provide to the agent
 * @return The successful result value
 * @throws Throwable if the execution fails
 * @throws AgentCancelledException if the execution is cancelled
 */
public suspend fun <Input, Output> AIAgent<Input, Output>.runOrThrow(input: Input): Output {
    return when (val outcome = runCancellable(input)) {
        is RunOutcome.Success -> outcome.value
        is RunOutcome.Failure -> throw outcome.error
        is RunOutcome.Cancelled -> throw AgentCancelledException(outcome.reason, outcome.message)
    }
}

/**
 * Executes the agent and maps the outcome using the provided transformation functions.
 *
 * This extension provides a functional approach to handling different outcomes,
 * allowing you to specify different behaviors for success, failure, and cancellation
 * cases. This is useful when you want to transform outcomes into a common result type.
 *
 * @param input The input to provide to the agent
 * @param onSuccess Function called when the execution succeeds
 * @param onFailure Function called when the execution fails
 * @param onCancelled Function called when the execution is cancelled
 * @return The result of calling the appropriate transformation function
 */
public suspend inline fun <Input, Output, R> AIAgent<Input, Output>.runAndMap(
    input: Input,
    onSuccess: (Output) -> R,
    onFailure: (Throwable) -> R,
    onCancelled: (CancellationReason, String?) -> R
): R {
    return when (val outcome = runCancellable(input)) {
        is RunOutcome.Success -> onSuccess(outcome.value)
        is RunOutcome.Failure -> onFailure(outcome.error)
        is RunOutcome.Cancelled -> onCancelled(outcome.reason, outcome.message)
    }
}

/**
 * Executes the agent and returns the result on success, or a default value on failure or cancellation.
 *
 * This extension provides a simple way to get a result with fallback behavior,
 * similar to the elvis operator but for agent execution outcomes.
 *
 * @param input The input to provide to the agent
 * @param defaultValue The value to return if execution fails or is cancelled
 * @return The successful result value, or the default value
 */
public suspend fun <Input, Output> AIAgent<Input, Output>.runOrDefault(
    input: Input,
    defaultValue: Output
): Output {
    return when (val outcome = runCancellable(input)) {
        is RunOutcome.Success -> outcome.value
        is RunOutcome.Failure, is RunOutcome.Cancelled -> defaultValue
    }
}

/**
 * Executes the agent and returns the result on success, or null on failure or cancellation.
 *
 * This extension provides a simple way to get a nullable result,
 * useful when you want to check for success without exception handling.
 *
 * @param input The input to provide to the agent
 * @return The successful result value, or null
 */
public suspend fun <Input, Output> AIAgent<Input, Output>.runOrNull(input: Input): Output? {
    return when (val outcome = runCancellable(input)) {
        is RunOutcome.Success -> outcome.value
        is RunOutcome.Failure, is RunOutcome.Cancelled -> null
    }
}

/**
 * Executes the agent and returns a [Result] representing the outcome.
 *
 * This extension provides compatibility with Kotlin's standard [Result] type.
 * Cancellations are converted to failures with [AgentCancelledException].
 *
 * @param input The input to provide to the agent
 * @return A [Result] representing the outcome
 */
public suspend fun <Input, Output> AIAgent<Input, Output>.runCatching(input: Input): Result<Output> {
    return runCancellable(input).toResult()
}
