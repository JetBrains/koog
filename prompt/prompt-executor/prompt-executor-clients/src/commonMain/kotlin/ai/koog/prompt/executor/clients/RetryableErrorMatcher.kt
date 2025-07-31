package ai.koog.prompt.executor.clients

/**
 * Platform-independent interface for matching errors that should be retried.
 */
public interface RetryableErrorMatcher {
    /**
     * Checks if the given throwable matches the pattern for retryable errors.
     *
     * @param throwable The throwable to check
     * @return true if the throwable matches the pattern, false otherwise
     */
    public fun matches(throwable: Throwable): Boolean
}

/**
 * Creates a RetryableErrorMatcher for the specified exception type and optional message pattern.
 *
 * @param exceptionType The fully qualified name of the exception class
 * @param messagePattern Optional substring that must be present in the exception message to match
 * @return A RetryableErrorMatcher that matches the specified criteria
 */
public expect fun createRetryableErrorMatcher(
    exceptionType: String,
    messagePattern: String? = null
): RetryableErrorMatcher