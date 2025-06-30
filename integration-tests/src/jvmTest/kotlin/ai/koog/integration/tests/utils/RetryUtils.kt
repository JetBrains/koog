package ai.koog.integration.tests.utils

import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assumptions

/*
* The RetryExtension is not working with JUnit parametrized tests,
* so I had to add this workaround to skip/retry tests with @ParametrizedTest annotation.
* */
object RetryUtils {
    private const val GOOGLE_API_ERROR = "Field 'parts' is required for type with serial name"
    private const val GOOGLE_429_ERROR = "Error from GoogleAI API: 429 Too Many Requests"
    private const val GOOGLE_500_ERROR = "Error from GoogleAI API: 500 Internal Server Error"
    private const val GOOGLE_503_ERROR = "Error from GoogleAI API: 503 Service Unavailable"
    private const val ANTHROPIC_429_ERROR = "Error from Anthropic API: 429 Too Many Requests"
    private const val ANTHROPIC_502_ERROR = "Error from Anthropic API: 502 Bad Gateway"
    private const val OPENAI_500_ERROR = "Error from OpenAI API: 500 Internal Server Error"
    private const val OPENAI_503_ERROR = "Error from OpenAI API: 503 Service Unavailable"

    private fun isThirdPartyError(e: Throwable): Boolean {
        val errorMessages = listOf(
            GOOGLE_API_ERROR,
            GOOGLE_429_ERROR,
            GOOGLE_500_ERROR,
            GOOGLE_503_ERROR,
            ANTHROPIC_429_ERROR,
            ANTHROPIC_502_ERROR,
            OPENAI_500_ERROR,
            OPENAI_503_ERROR
        )

        val message = e.message
        return message != null && errorMessages.any { errorPattern ->
            message.contains(errorPattern, ignoreCase = true)
        }
    }

    suspend fun <T> withRetry(
        times: Int = 3,
        delayMs: Long = 1000,
        testName: String = "test",
        action: suspend () -> T
    ): T {
        var lastException: Throwable? = null

        for (attempt in 1..times) {
            try {
                println("[DEBUG_LOG] Test '$testName' - attempt $attempt of $times")
                val result = action()
                println("[DEBUG_LOG] Test '$testName' succeeded on attempt $attempt")
                return result
            } catch (throwable: Throwable) {
                lastException = throwable

                if (isThirdPartyError(throwable)) {
                    println("[DEBUG_LOG] Skipping test due to third-party service error: ${throwable.message}")
                    Assumptions.assumeTrue(
                        false,
                        "Skipping test due to third-party service error: ${throwable.message}"
                    )
                    return action()
                }

                println("[DEBUG_LOG] Test '$testName' failed on attempt $attempt: ${throwable.message}")

                if (attempt < times) {
                    println("[DEBUG_LOG] Retrying test '$testName' (attempt ${attempt + 1} of $times)")

                    if (delayMs > 0) {
                        delay(delayMs)
                    }
                } else {
                    println("[DEBUG_LOG] Maximum retry attempts ($times) reached for test '$testName'")
                }
            }
        }

        throw lastException!!
    }
}