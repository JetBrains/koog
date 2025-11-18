package ai.koog.integration.tests.utils

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions

/*
* The RetryExtension is not working with JUnit parametrized tests,
* so I had to add this workaround to skip/retry tests with @ParametrizedTest annotation.
* */
object RetryUtils {
    private const val GOOGLE_429_ERROR = "Error from GoogleAI API: 429 Too Many Requests"
    private const val GOOGLE_500_ERROR = "Error from GoogleAI API: 500 Internal Server Error"
    private const val GOOGLE_503_ERROR = "Error from GoogleAI API: 503 Service Unavailable"
    private const val ANTHROPIC_429_ERROR = "Error from Anthropic API: 429 Too Many Requests"
    private const val ANTHROPIC_500_ERROR = "Error from Anthropic API: 500 Internal Server Error"
    private const val ANTHROPIC_502_ERROR = "Error from Anthropic API: 502 Bad Gateway"
    private const val ANTHROPIC_529_ERROR = "Error from Anthropic API: 529"
    private const val OPENAI_500_ERROR = "Error from OpenAI API: 500 Internal Server Error"
    private const val OPENAI_503_ERROR = "Error from OpenAI API: 503 Service Unavailable"
    private const val OPENAI_LLM_CLIENT_500_ERROR = "Error from OpenAILLMClient API: 500 Internal Server Error"

    // As we can't do anything about how OpenRouter returns responses from time to time,
    // it's not worth failing tests on a 3-rd party conditions.
    private const val OPEN_ROUTER_PARTS_ERROR =
        "Field 'id' is required for type with serial name 'ai.koog.prompt.executor.clients.openai.base.models.OpenAIToolCall', but it was missing at path:"

    // External image URL download failures are third-party service issues
    private const val OPENAI_IMAGE_DOWNLOAD_ERROR = "Error while downloading"

    private fun isThirdPartyError(e: Throwable): Boolean {
        val errorMessages = listOf(
            GOOGLE_429_ERROR,
            GOOGLE_500_ERROR,
            GOOGLE_503_ERROR,
            ANTHROPIC_429_ERROR,
            ANTHROPIC_500_ERROR,
            ANTHROPIC_502_ERROR,
            ANTHROPIC_529_ERROR,
            OPENAI_500_ERROR,
            OPENAI_503_ERROR,
            OPENAI_LLM_CLIENT_500_ERROR,
            OPENAI_IMAGE_DOWNLOAD_ERROR,
        )

        val message = e.message
        return message != null &&
            errorMessages.any { errorPattern ->
                message.contains(errorPattern, ignoreCase = true)
            }
    }

    fun <T> withRetry(
        times: Int = 3,
        delayMs: Long = 1000,
        testName: String = "test",
        action: suspend () -> T
    ): T = runBlocking {
        var lastException: Throwable? = null

        for (attempt in 1..times) {
            try {
                val result = action()
                return@runBlocking result
            } catch (throwable: Throwable) {
                lastException = throwable

                if (isThirdPartyError(throwable)) {
                    println("[DEBUG_LOG] Skipping test due to third-party service error: ${throwable.message}")
                    Assumptions.assumeTrue(
                        false,
                        "Skipping test due to third-party service error: ${throwable.message}"
                    )
                    return@runBlocking action()
                }

                if (attempt < times) {
                    if (delayMs > 0) {
                        delay(delayMs)
                    }
                } else if (throwable.message?.contains(OPEN_ROUTER_PARTS_ERROR) == true) {
                    println("[DEBUG_LOG] Skipping test due to OpenRouter error: ${throwable.message}")
                    Assumptions.assumeTrue(
                        false,
                        "Skipping test due to OpenRouter error: ${throwable.message} after $times attempts"
                    )
                } else {
                    println("[DEBUG_LOG] Maximum retry attempts ($times) reached for test '$testName'")
                }
            }
        }

        throw lastException!!
    }
}
