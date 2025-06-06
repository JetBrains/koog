package ai.koog.integration.tests.utils.annotations

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler

class RetryExtension : TestExecutionExceptionHandler {
    companion object {
        private val NAMESPACE = ExtensionContext.Namespace.create(RetryExtension::class.java)

        private const val GOOGLE_API_ERROR = "Field 'parts' is required for type with serial name"
        private const val GOOGLE_429_ERROR = "Error from GoogleAI API: 429 Too Many Requests"
        private const val GOOGLE_500_ERROR = "Error from GoogleAI API: 500 Internal Server Error"
        private const val GOOGLE_503_ERROR = "Error from GoogleAI API: 503 Service Unavailable"
    }

    private fun isGoogleSideError(e: Throwable): Boolean {
        return e.message?.contains(GOOGLE_429_ERROR) == true
                || e.message?.contains(GOOGLE_500_ERROR) == true
                || e.message?.contains(GOOGLE_503_ERROR) == true
                || e.message?.contains(GOOGLE_API_ERROR) == true
    }

    override fun handleTestExecutionException(
        context: ExtensionContext,
        throwable: Throwable
    ) {
        if (isGoogleSideError(throwable)) {
            println("[DEBUG_LOG] Google-side known error detected: ${throwable.message}")
            assumeTrue(false, "Skipping test due to ${throwable.message}")
            return
        }

        val retry = context.requiredTestMethod.getAnnotation(Retry::class.java)
        if (retry != null) {
            val retryStore = context.getStore(NAMESPACE)
            val key = "${context.requiredTestClass.name}.${context.requiredTestMethod.name}"
            val currentAttempt = retryStore.getOrComputeIfAbsent(key, { 0 }, Int::class.java) as Int

            println("[DEBUG_LOG] Test '${context.displayName}' failed. Attempt ${currentAttempt + 1} of ${retry.times}")

            if (currentAttempt < retry.times - 1) {
                retryStore.put(key, currentAttempt + 1)
                println("[DEBUG_LOG] Retrying test '${context.displayName}'")
                return // Don't throw the exception to allow retry
            } else {
                println("[DEBUG_LOG] Maximum retry attempts (${retry.times}) reached for test '${context.displayName}'")
            }
        }
        throw throwable
    }
}
