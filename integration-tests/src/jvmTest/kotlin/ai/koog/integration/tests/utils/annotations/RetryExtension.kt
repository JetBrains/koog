package ai.koog.integration.tests.utils.annotations

import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler

class RetryExtension : TestExecutionExceptionHandler {
    companion object {
        private val NAMESPACE = ExtensionContext.Namespace.create(RetryExtension::class.java)
    }

    override fun handleTestExecutionException(
        context: ExtensionContext,
        throwable: Throwable
    ) {
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
