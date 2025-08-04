package ai.koog.agents.core.agent

import kotlinx.datetime.Instant

/**
 * Represents the tri-state outcome of an agent execution.
 *
 * This sealed interface provides a clean way to distinguish between the three
 * possible outcomes of an agent run: successful completion, failure due to an error,
 * or cancellation. This is semantically more accurate than treating cancellation
 * as either success or failure.
 *
 * The tri-state design enables:
 * - Proper retry logic (don't retry cancellations)
 * - Accurate telemetry and observability
 * - Different error handling for different outcome types
 * - Clear UX distinctions between error states and user-initiated stops
 */
public sealed interface RunOutcome<out T> {

    /**
     * Represents successful completion of an agent execution.
     *
     * @param value The result value produced by the agent execution
     */
    public data class Success<T>(val value: T) : RunOutcome<T>

    /**
     * Represents a failed agent execution due to an error.
     *
     * This outcome indicates that the agent encountered an unexpected condition
     * or exception that prevented successful completion.
     *
     * @param error The throwable that caused the execution to fail
     */
    public data class Failure(val error: Throwable) : RunOutcome<Nothing>

    /**
     * Represents an agent execution that was cancelled before completion.
     *
     * This outcome is distinct from failure - it indicates that the execution
     * was intentionally stopped rather than encountering an error condition.
     *
     * @param reason The reason why the execution was cancelled
     * @param message Optional descriptive message about the cancellation
     * @param cancelledAt Timestamp when the cancellation occurred
     */
    public data class Cancelled(
        val reason: CancellationReason,
        val message: String? = null,
        val cancelledAt: Instant = kotlinx.datetime.Clock.System.now()
    ) : RunOutcome<Nothing>
}

/**
 * Extension function to check if the outcome represents a successful execution.
 *
 * @return true if this outcome is [RunOutcome.Success], false otherwise
 */
public fun <T> RunOutcome<T>.isSuccess(): Boolean = this is RunOutcome.Success<T>

/**
 * Extension function to check if the outcome represents a failed execution.
 *
 * @return true if this outcome is [RunOutcome.Failure], false otherwise
 */
public fun <T> RunOutcome<T>.isFailure(): Boolean = this is RunOutcome.Failure

/**
 * Extension function to check if the outcome represents a cancelled execution.
 *
 * @return true if this outcome is [RunOutcome.Cancelled], false otherwise
 */
public fun <T> RunOutcome<T>.isCancelled(): Boolean = this is RunOutcome.Cancelled

/**
 * Extension function to get the success value, or null if not successful.
 *
 * @return the success value if this is [RunOutcome.Success], null otherwise
 */
public fun <T> RunOutcome<T>.getOrNull(): T? = when (this) {
    is RunOutcome.Success -> value
    is RunOutcome.Failure -> null
    is RunOutcome.Cancelled -> null
}

/**
 * Calls the specified function [action] with the value if this outcome represents success.
 * Returns the original outcome unchanged.
 *
 * @param action function to invoke with the success value
 * @return the original outcome
 */
public inline fun <T> RunOutcome<T>.onSuccess(action: (T) -> Unit): RunOutcome<T> {
    if (this is RunOutcome.Success) action(value)
    return this
}

/**
 * Calls the specified function [action] with the error if this outcome represents failure.
 * Returns the original outcome unchanged.
 *
 * @param action function to invoke with the error
 * @return the original outcome
 */
public inline fun <T> RunOutcome<T>.onFailure(action: (Throwable) -> Unit): RunOutcome<T> {
    if (this is RunOutcome.Failure) action(error)
    return this
}

/**
 * Calls the specified function [action] with the cancellation details if this outcome represents cancellation.
 * Returns the original outcome unchanged.
 *
 * @param action function to invoke with the cancellation reason and message
 * @return the original outcome
 */
public inline fun <T> RunOutcome<T>.onCancelled(action: (CancellationReason, String?) -> Unit): RunOutcome<T> {
    if (this is RunOutcome.Cancelled) action(reason, message)
    return this
}

/**
 * Returns a [Result] that represents the same outcome as this [RunOutcome].
 *
 * Cancellations are converted to failures with [AgentCancelledException].
 * This provides compatibility with Kotlin's standard [Result] type.
 *
 * @return a [Result] representing this outcome
 */
public fun <T> RunOutcome<T>.toResult(): Result<T> = when (this) {
    is RunOutcome.Success -> Result.success(value)
    is RunOutcome.Failure -> Result.failure(error)
    is RunOutcome.Cancelled -> Result.failure(AgentCancelledException(reason, message))
}

/**
 * Exception thrown when an agent execution is cancelled.
 *
 * This exception is used when converting [RunOutcome.Cancelled] to other result types
 * that need to represent cancellation as an exception.
 *
 * @property reason The reason why the agent was cancelled
 * @property message Optional descriptive message about the cancellation
 */
public class AgentCancelledException(
    public val reason: CancellationReason,
    message: String? = null
) : Exception(message ?: "Agent execution was cancelled: ${reason.name}")
