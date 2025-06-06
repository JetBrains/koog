package ai.koog.integration.tests.utils.annotations

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.extension.*
import java.util.stream.Stream

class RetryExtension : TestTemplateInvocationContextProvider, TestExecutionExceptionHandler {
    companion object {
        private val NAMESPACE = ExtensionContext.Namespace.create(RetryExtension::class.java)

        private const val GOOGLE_API_ERROR = "Field 'parts' is required for type with serial name"
        private const val GOOGLE_429_ERROR = "Error from GoogleAI API: 429 Too Many Requests"
        private const val GOOGLE_500_ERROR = "Error from GoogleAI API: 500 Internal Server Error"
        private const val GOOGLE_503_ERROR = "Error from GoogleAI API: 503 Service Unavailable"
        private const val ANTHROPIC_502_ERROR = "Error from Anthropic API: 502 Bad Gateway"
    }

    private fun isThirdSideError(e: Throwable): Boolean {
        return e.message?.contains(GOOGLE_429_ERROR) == true
                || e.message?.contains(GOOGLE_500_ERROR) == true
                || e.message?.contains(GOOGLE_503_ERROR) == true
                || e.message?.contains(GOOGLE_API_ERROR) == true
                || e.message?.contains(ANTHROPIC_502_ERROR) == true
    }

    override fun supportsTestTemplate(context: ExtensionContext): Boolean {
        return context.requiredTestMethod.isAnnotationPresent(Retry::class.java)
    }

    override fun provideTestTemplateInvocationContexts(context: ExtensionContext): Stream<TestTemplateInvocationContext> {
        val retry = context.requiredTestMethod.getAnnotation(Retry::class.java)
        return Stream.generate { createInvocationContext(context, retry) }
            .limit(retry.times.toLong())
    }

    private fun createInvocationContext(context: ExtensionContext, retry: Retry): TestTemplateInvocationContext {
        return object : TestTemplateInvocationContext {
            override fun getDisplayName(invocationIndex: Int): String {
                return "${context.displayName} [attempt ${invocationIndex}]"
            }

            override fun getAdditionalExtensions(): List<Extension> {
                return listOf(RetryExecutionExtension(retry))
            }
        }
    }

    override fun handleTestExecutionException(
        context: ExtensionContext,
        throwable: Throwable
    ) {
        if (isThirdSideError(throwable)) {
            println("[DEBUG_LOG] Google-side known error detected: ${throwable.message}")
            assumeTrue(false, "Skipping test due to ${throwable.message}")
            return
        }

        throw throwable
    }

    private class RetryExecutionExtension(private val retry: Retry) : TestExecutionExceptionHandler {
        override fun handleTestExecutionException(
            context: ExtensionContext,
            throwable: Throwable
        ) {
            val retryStore = context.getStore(NAMESPACE)
            val key = "${context.requiredTestClass.name}.${context.requiredTestMethod.name}"
            val currentAttempt = retryStore.getOrComputeIfAbsent(key, { 0 }, Int::class.java) as Int

            println("[DEBUG_LOG] Test '${context.displayName}' failed. Attempt ${currentAttempt + 1} of ${retry.times}")

            if (currentAttempt < retry.times - 1) {
                retryStore.put(key, currentAttempt + 1)
                println("[DEBUG_LOG] Retrying test '${context.displayName}'")
                return
            } else {
                println("[DEBUG_LOG] Maximum retry attempts (${retry.times}) reached for test '${context.displayName}'")
                throw throwable
            }
        }
    }
}