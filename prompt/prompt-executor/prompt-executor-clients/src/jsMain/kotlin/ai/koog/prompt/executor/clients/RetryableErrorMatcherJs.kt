package ai.koog.prompt.executor.clients

/**
 * JS implementation of RetryableErrorMatcher.
 */
public actual fun createRetryableErrorMatcher(
    exceptionType: String,
    messagePattern: String?
): RetryableErrorMatcher = JsRetryableErrorMatcher(exceptionType, messagePattern)

private class JsRetryableErrorMatcher(
    private val exceptionType: String,
    private val messagePattern: String?
) : RetryableErrorMatcher {
    override fun matches(throwable: Throwable): Boolean {
        // In JS, we can't use Java reflection, so we'll do a simple check based on the class name
        val throwableClassName = throwable::class.simpleName ?: ""
        val expectedClassName = exceptionType.substringAfterLast('.')
        
        if (throwableClassName != expectedClassName) {
            return false
        }
        
        return messagePattern == null || (throwable.message ?: "").contains(messagePattern)
    }
}