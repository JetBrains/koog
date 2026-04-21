package ai.koog.integration.tests.utils

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions
import java.net.HttpURLConnection
import java.net.URL

/*
* The RetryExtension is not working with JUnit parametrized tests,
* so I had to add this workaround to skip/retry tests with @ParametrizedTest annotation.
* */
object RetryUtils {
    private const val GOOGLE_429_ERROR = "Expected status code 200 but was 429"
    private const val GOOGLE_RESOURCE_EXHAUSTED =
        "You exceeded your current quota, please check your plan and billing details"
    private const val GOOGLE_QUOTA_EXCEEDED = "Quota exceeded"
    private const val GOOGLE_RESOURCE_EXHAUSTED_STATUS = "RESOURCE_EXHAUSTED"
    private const val GOOGLE_429_STATUS = "Status code: 429"
    private const val GOOGLE_500_ERROR = "Error from GoogleAI API: 500 Internal Server Error"
    private const val GOOGLE_503_ERROR = "Error from GoogleAI API: 503 Service Unavailable"
    private const val GOOGLE_UNAVAILABLE_STATUS = "\"status\": \"UNAVAILABLE\""
    private const val GOOGLE_HIGH_DEMAND_ERROR = "This model is currently experiencing high demand"
    private const val ANTHROPIC_429_ERROR = "Error from Anthropic API: 429 Too Many Requests"
    private const val ANTHROPIC_500_ERROR = "Error from Anthropic API: 500 Internal Server Error"
    private const val ANTHROPIC_502_ERROR = "Error from Anthropic API: 502 Bad Gateway"
    private const val ANTHROPIC_529_ERROR = "Error from Anthropic API: 529"
    private const val OPENAI_500_ERROR = "Error from OpenAI API: 500 Internal Server Error"
    private const val OPENAI_503_ERROR = "Error from OpenAI API: 503 Service Unavailable"
    private const val OPENAI_LLM_CLIENT_500_ERROR = "Error from OpenAILLMClient API: 500 Internal Server Error"
    private const val OPEN_ROUTER_502_ERROR = "{\"error\":{\"message\":\"Provider returned error\",\"code\":502"
    private const val MISTRAL_502_ERROR = "Error from client: MistralAILLMClient\nStatus code: 502"
    private const val BEDROCK_MARKETPLACE_ACCESS_DENIED =
        "Model access is denied due to IAM user or service role is not authorized to perform the required AWS Marketplace actions"

    // As we can't do anything about how OpenRouter returns responses from time to time,
    // it's not worth failing tests on a 3-rd party conditions.
    private const val OPEN_ROUTER_PARTS_ERROR =
        "Field 'id' is required for type with serial name 'ai.koog.prompt.executor.clients.openai.base.models.OpenAIToolCall', but it was missing at path:"

    // External image URL download failures are third-party service issues
    private const val OPENAI_IMAGE_DOWNLOAD_ERROR = "Error while downloading"
    private const val URL_CONNECT_TIMEOUT_MS = 5_000
    private const val URL_READ_TIMEOUT_MS = 10_000

    private fun isThirdPartyError(e: Throwable): Boolean {
        val errorMessages = listOf(
            GOOGLE_429_ERROR,
            GOOGLE_429_STATUS,
            GOOGLE_RESOURCE_EXHAUSTED,
            GOOGLE_QUOTA_EXCEEDED,
            GOOGLE_RESOURCE_EXHAUSTED_STATUS,
            GOOGLE_500_ERROR,
            GOOGLE_503_ERROR,
            GOOGLE_UNAVAILABLE_STATUS,
            GOOGLE_HIGH_DEMAND_ERROR,
            ANTHROPIC_429_ERROR,
            ANTHROPIC_500_ERROR,
            ANTHROPIC_502_ERROR,
            ANTHROPIC_529_ERROR,
            OPENAI_500_ERROR,
            OPENAI_503_ERROR,
            OPENAI_LLM_CLIENT_500_ERROR,
            OPENAI_IMAGE_DOWNLOAD_ERROR,
            OPEN_ROUTER_502_ERROR,
            MISTRAL_502_ERROR,
            BEDROCK_MARKETPLACE_ACCESS_DENIED
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

    @JvmStatic
    @JvmOverloads
    fun ensureUrlAccessible(
        url: String,
        times: Int = 3,
        delayMs: Long = 500,
        testName: String = "url accessibility check"
    ) {
        withRetry(times = times, delayMs = delayMs, testName = "$testName: $url") {
            openConnection(url).run {
                try {
                    val statusCode = responseCode
                    check(statusCode in 200..299) {
                        "Remote URL is not accessible: $url returned HTTP $statusCode"
                    }
                } finally {
                    disconnect()
                }
            }
        }
    }

    private fun openConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = URL_CONNECT_TIMEOUT_MS
            readTimeout = URL_READ_TIMEOUT_MS
            instanceFollowRedirects = true
        }
}
