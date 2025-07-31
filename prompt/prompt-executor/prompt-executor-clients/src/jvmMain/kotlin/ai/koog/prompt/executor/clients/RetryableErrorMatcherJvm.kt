package ai.koog.prompt.executor.clients

/**
 * JVM implementation of RetryableErrorMatcher that uses Java reflection.
 */
public actual fun createRetryableErrorMatcher(
    exceptionType: String,
    messagePattern: String?
): RetryableErrorMatcher = JvmRetryableErrorMatcher(exceptionType, messagePattern)

private class JvmRetryableErrorMatcher(
    private val exceptionType: String,
    private val messagePattern: String?
) : RetryableErrorMatcher {
    private val exceptionClass: Class<out Throwable> by lazy {
        try {
            @Suppress("UNCHECKED_CAST")
            Class.forName(exceptionType) as Class<out Throwable>
        } catch (e: ClassNotFoundException) {
            throw IllegalArgumentException("Exception class not found: $exceptionType", e)
        }
    }

    override fun matches(throwable: Throwable): Boolean {
        if (!exceptionClass.isInstance(throwable)) {
            return false
        }
        
        return messagePattern == null || (throwable.message ?: "").contains(messagePattern)
    }
}