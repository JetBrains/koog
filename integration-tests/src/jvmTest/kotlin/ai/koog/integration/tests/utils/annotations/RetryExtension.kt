package ai.koog.integration.tests.utils.annotations

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.InvocationInterceptor
import org.junit.jupiter.api.extension.ReflectiveInvocationContext
import org.opentest4j.TestAbortedException
import java.lang.reflect.Method

class RetryExtension : InvocationInterceptor {
    companion object {
        private const val GOOGLE_API_ERROR = "Field 'parts' is required for type with serial name"
        private const val GOOGLE_429_ERROR = "Error from GoogleAI API: 429 Too Many Requests"
        private const val GOOGLE_500_ERROR = "Error from GoogleAI API: 500 Internal Server Error"
        private const val GOOGLE_503_ERROR = "Error from GoogleAI API: 503 Service Unavailable"
        private const val ANTHROPIC_502_ERROR = "Error from Anthropic API: 502 Bad Gateway"
        private const val OPENAI_500_ERROR = "Error from OpenAI API: 500 Internal Server Error"
        private const val OPENAI_503_ERROR = "Error from OpenAI API: 503 Service Unavailable"
        private const val MISTRAL_503_ERROR = "Error from MistralAILLMClient API: 503 Service Unavailable"
    }

    private fun isThirdPartyError(e: Throwable): Boolean {
        val errorMessages =
            listOf(
                GOOGLE_API_ERROR,
                GOOGLE_429_ERROR,
                GOOGLE_500_ERROR,
                GOOGLE_503_ERROR,
                ANTHROPIC_502_ERROR,
                OPENAI_500_ERROR,
                OPENAI_503_ERROR,
                MISTRAL_503_ERROR,
            )
        return e.message?.let { message -> message in errorMessages } == true
    }

    override fun interceptTestMethod(
        invocation: InvocationInterceptor.Invocation<Void>,
        invocationContext: ReflectiveInvocationContext<Method>,
        extensionContext: ExtensionContext
    ) {
        val retry = extensionContext.requiredTestMethod.getAnnotation(Retry::class.java)

        if (retry == null) {
            invocation.proceed()
            return
        }

        var lastException: Throwable? = null
        var attempt = 0

        while (attempt < retry.times) {
            attempt++
            try {
                println("[DEBUG_LOG] Test '${extensionContext.displayName}' - attempt $attempt of ${retry.times}")
                if (attempt == 1) {
                    invocation.proceed()
                } else {
                    invokeTestMethodDirectly(invocationContext, extensionContext)
                }
                println("[DEBUG_LOG] Test '${extensionContext.displayName}' succeeded on attempt $attempt")
                return
            } catch (throwable: Throwable) {
                lastException = throwable

                if (throwable is TestAbortedException) {
                    println("[DEBUG_LOG] Test skipped due to assumption failure: ${throwable.message}")
                    throw throwable
                }

                if (isThirdPartyError(throwable)) {
                    println("[DEBUG_LOG] Third-party service error detected: ${throwable.message}")
                    assumeTrue(false, "Skipping test due to ${throwable.message}")
                    return
                }

                println(
                    "[DEBUG_LOG] Test '${extensionContext.displayName}' failed on attempt $attempt: ${throwable.message}"
                )

                if (attempt < retry.times) {
                    println(
                        "[DEBUG_LOG] Retrying test '${extensionContext.displayName}' (attempt ${attempt + 1} of ${retry.times})"
                    )

                    if (retry.delayMs > 0) {
                        try {
                            Thread.sleep(retry.delayMs)
                        } catch (e: InterruptedException) {
                            Thread.currentThread().interrupt()
                            throw e
                        }
                    }
                } else {
                    println(
                        "[DEBUG_LOG] Maximum retry attempts (${retry.times}) reached for test '${extensionContext.displayName}'"
                    )
                }
            }
        }

        throw lastException!!
    }

    private fun invokeTestMethodDirectly(
        invocationContext: ReflectiveInvocationContext<Method>,
        extensionContext: ExtensionContext
    ) {
        val testInstance = extensionContext.requiredTestInstance
        val testMethod = invocationContext.executable
        val arguments = invocationContext.arguments

        testMethod.invoke(testInstance, *arguments.toTypedArray())
    }
}
