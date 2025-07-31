package ai.koog.prompt.executor.clients

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlin.random.Random


/**
 * A wrapper for LLMClient that adds retry logic for connection errors and rate limiting.
 */
public class LLMClientWithRetry(
    private val delegate: LLMClient,
    private val maxRetries: Int = 5,
    private val initialDelayMs: Long = 1000,
    private val retryableErrorMatchers: List<RetryableErrorMatcher> = defaultRetryableErrorMatchers,
) : LLMClient {
    private companion object {
        private val logger = KotlinLogging.logger { }
        
        val defaultRetryableErrorMatchers = listOf(
            createRetryableErrorMatcher("ai.koog.agents.core.exception.UnexpectedServerException"),
            createRetryableErrorMatcher("java.io.EOFException", "Failed to parse HTTP response: the server prematurely closed the connection"),
            createRetryableErrorMatcher("java.lang.IllegalStateException", "Error from Anthropic API: 529")
        )
    }
    
    private fun calculateBackoffDelayMs(retryCount: Int): Long {
        val baseDelayMs = initialDelayMs * (1L shl retryCount)
        val jitterFactor = 0.5 + Random.nextDouble()
        return (baseDelayMs * jitterFactor).toLong()
    }
    
    private suspend fun waitForRetryOrRethrow(e: Throwable, retryCount: Int) {
        if(retryCount < maxRetries) {
            val delayMillis = calculateBackoffDelayMs(retryCount)
            logger.warn {
                "Server side error detected. Retrying (${retryCount}/$maxRetries) after ${delayMillis}ms delay..."
            }
            delay(delayMillis)
        } else {
            logger.error(e) { "Failed to execute LLM request after $maxRetries retries" }
            throw RuntimeException(
                "LLM provider Server side error after $maxRetries retries: ${e.message}",
                e
            )
        }
    }

    public override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
        var retryCount = 0

        while (true) {
            try {
                return delegate.execute(prompt, model, tools)
            } catch (e: Throwable) {
                val shouldRetry = retryableErrorMatchers.any { it.matches(e) }
                if (!shouldRetry) {
                    throw e
                }
                
                retryCount += 1
                waitForRetryOrRethrow(e, retryCount)
            }
        }
    }

    public override fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> {
        return delegate.executeStreaming(prompt, model)
    }

    public override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
        return delegate.moderate(prompt, model)
    }
}