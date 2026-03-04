package ai.koog.integration.tests.utils.annotations

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.InvocationInterceptor
import org.junit.jupiter.api.extension.ReflectiveInvocationContext
import org.opentest4j.TestAbortedException
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.CountDownLatch
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

class RetryExtension : InvocationInterceptor {
    companion object {
        private const val GOOGLE_API_ERROR = "Field 'parts' is required for type with serial name"
        private const val GOOGLE_429_ERROR = "Error from GoogleAI API: 429 Too Many Requests"
        private const val GOOGLE_RESOURCE_EXHAUSTED =
            "You exceeded your current quota, please check your plan and billing details"
        private const val GOOGLE_QUOTA_EXCEEDED = "Quota exceeded"
        private const val GOOGLE_RESOURCE_EXHAUSTED_STATUS = "RESOURCE_EXHAUSTED"
        private const val GOOGLE_429_STATUS = "Status code: 429"
        private const val GOOGLE_500_ERROR = "Error from GoogleAI API: 500 Internal Server Error"
        private const val GOOGLE_503_ERROR = "Error from GoogleAI API: 503 Service Unavailable"
        private const val ANTHROPIC_502_ERROR = "Error from Anthropic API: 502 Bad Gateway"
        private const val OPENAI_500_ERROR = "Error from OpenAI API: 500 Internal Server Error"
        private const val OPENAI_503_ERROR = "Error from OpenAI API: 503 Service Unavailable"
        private const val MISTRAL_503_ERROR = "Error from MistralAILLMClient API: 503 Service Unavailable"
        private const val OPENROUTER_API_ERROR = "unknown error in the model inference server"
    }

    private fun isThirdPartyError(e: Throwable): Boolean {
        val errorMessages =
            listOf(
                GOOGLE_API_ERROR,
                GOOGLE_429_ERROR,
                GOOGLE_429_STATUS,
                GOOGLE_RESOURCE_EXHAUSTED,
                GOOGLE_QUOTA_EXCEEDED,
                GOOGLE_RESOURCE_EXHAUSTED_STATUS,
                GOOGLE_500_ERROR,
                GOOGLE_503_ERROR,
                ANTHROPIC_502_ERROR,
                OPENAI_500_ERROR,
                OPENAI_503_ERROR,
                MISTRAL_503_ERROR,
                OPENROUTER_API_ERROR,
            )
        val message = e.message
        return message != null && errorMessages.any { message.contains(it, ignoreCase = true) }
    }

    override fun interceptTestMethod(
        invocation: InvocationInterceptor.Invocation<Void?>,
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

        executeBeforeTestMethods(testInstance)

        try {
            if (isSuspendFunction(testMethod)) {
                invokeSuspendMethod(testMethod, testInstance, arguments)
            } else {
                testMethod.invoke(testInstance, *arguments.toTypedArray())
            }
        } catch (e: InvocationTargetException) {
            // Unwrap reflection wrapper so callers see the actual test exception,
            // consistent with the first-attempt path via invocation.proceed()
            throw e.cause ?: e
        } finally {
            executeAfterTestMethods(testInstance)
        }
    }

    private fun isSuspendFunction(method: Method): Boolean {
        val params = method.parameterTypes
        return params.isNotEmpty() && Continuation::class.java.isAssignableFrom(params.last())
    }

    private fun invokeSuspendMethod(method: Method, testInstance: Any, arguments: List<Any?>) {
        val latch = CountDownLatch(1)
        var error: Throwable? = null

        val continuation = Continuation<Any?>(EmptyCoroutineContext) { result ->
            error = result.exceptionOrNull()
            latch.countDown()
        }

        val allArgs = arguments.toTypedArray() + continuation
        val result = method.invoke(testInstance, *allArgs)

        if (result === COROUTINE_SUSPENDED) {
            latch.await()
            error?.let { throw it }
        }
    }

    private fun <T : Annotation> executeAnnotatedMethods(testInstance: Any, annotationClass: Class<T>) {
        // Find all methods in the test class that are annotated with the specified annotation
        val annotatedMethods = testInstance.javaClass.methods
            .filter { method -> method.isAnnotationPresent(annotationClass) }

        // Execute each annotated method
        annotatedMethods.forEach { method ->
            method.isAccessible = true
            method.invoke(testInstance)
        }
    }

    private fun executeBeforeTestMethods(testInstance: Any) {
        executeAnnotatedMethods(testInstance, BeforeTest::class.java)
    }

    private fun executeAfterTestMethods(testInstance: Any) {
        executeAnnotatedMethods(testInstance, AfterTest::class.java)
    }
}
