package ai.koog.agents.core.agent

import kotlinx.coroutines.Deferred

/**
 * Represents a cancellable agent execution with external control capabilities.
 *
 * This class provides the foundation for external cancellation scenarios like:
 * - CLI applications with keyboard interruption (Escape key)
 * - Web requests that can be cancelled by client disconnect
 * - Background jobs with external cancellation APIs
 * - Interactive applications requiring responsive user cancellation
 *
 * **Usage Example:**
 * ```kotlin
 * // Start agent with cancellation capability
 * val execution = agent.startCancellable("user input")
 *
 * // In parallel: monitor for cancellation signals
 * launch {
 *     if (userPressedEscape()) {
 *         execution.cancel(CancellationReason.UserRequested)
 *     }
 * }
 *
 * // Await result with proper tri-state outcome
 * val outcome = execution.outcome.await()
 * when (outcome) {
 *     is RunOutcome.Success -> handleSuccess(outcome.value)
 *     is RunOutcome.Cancelled -> handleCancellation(outcome.reason)
 *     is RunOutcome.Failure -> handleFailure(outcome.error)
 * }
 * ```
 *
 * @param Output The type of successful agent output
 * @property outcome Deferred result that can be awaited for the final outcome
 * @property cancel Function to cancel the execution with a specific reason
 */
public data class CancellableExecution<Output>(
    /**
     * The deferred agent execution outcome.
     *
     * Await this to get the final [RunOutcome] which will be:
     * - [RunOutcome.Success] if the agent completes successfully
     * - [RunOutcome.Cancelled] if [cancel] is called before completion
     * - [RunOutcome.Failure] if the agent encounters an error
     */
    val outcome: Deferred<RunOutcome<Output>>,

    /**
     * Cancel the agent execution with the specified reason.
     *
     * This function is thread-safe and idempotent - calling it multiple times
     * has no additional effect. The cancellation will cause [outcome] to
     * resolve to [RunOutcome.Cancelled] with the provided reason.
     *
     * @param reason The semantic reason for cancellation (defaults to UserRequested)
     */
    val cancel: (reason: CancellationReason) -> Unit
) {

    /**
     * Whether the agent execution is still active (not completed, failed, or cancelled).
     */
    val isActive: Boolean get() = outcome.isActive

    /**
     * Whether the agent execution has completed (successfully, failed, or been cancelled).
     */
    val isCompleted: Boolean get() = outcome.isCompleted

    /**
     * Convenience method to cancel with user-requested reason.
     *
     * Equivalent to calling `cancel(CancellationReason.UserRequested)`.
     */
    public fun cancelByUser(): Unit = cancel(CancellationReason.UserRequested)

    /**
     * Convenience method to cancel due to timeout.
     *
     * Equivalent to calling `cancel(CancellationReason.Timeout)`.
     */
    public fun cancelByTimeout(): Unit = cancel(CancellationReason.Timeout)

    /**
     * Convenience method to cancel due to policy violation.
     *
     * Equivalent to calling `cancel(CancellationReason.Policy)`.
     */
    public fun cancelByPolicy(): Unit = cancel(CancellationReason.Policy)

    /**
     * Convenience method to cancel due to system issues.
     *
     * Equivalent to calling `cancel(CancellationReason.System)`.
     */
    public fun cancelBySystem(): Unit = cancel(CancellationReason.System)
}
