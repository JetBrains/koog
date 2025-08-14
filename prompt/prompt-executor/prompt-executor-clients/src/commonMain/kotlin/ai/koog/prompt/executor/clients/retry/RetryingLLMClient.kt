package ai.koog.prompt.executor.clients.retry

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.model.LLMChoice
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * A decorator that adds retry capabilities to any LLMClient implementation.
 *
 * This is a pure decorator - it has no knowledge of specific providers or implementations.
 * It simply wraps any LLMClient and retries operations based on configurable policies.
 *
 * Example usage:
 * ```kotlin
 * val client = AnthropicLLMClient(apiKey)
 * val retryingClient = RetryingLLMClient(client, RetryConfig.CONSERVATIVE)
 * ```
 *
 * @param delegate The LLMClient to wrap with retry logic
 * @param config Configuration for retry behavior
 */
public class RetryingLLMClient(
    private val delegate: LLMClient,
    private val config: RetryConfig = RetryConfig()
) : LLMClient {

    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<Message.Response> = withRetry("execute") {
        delegate.execute(prompt, model, tools)
    }

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel
    ): Flow<String> {
        if (!config.enableStreamingRetry) {
            // Simple passthrough if streaming retry is disabled
            return delegate.executeStreaming(prompt, model)
        }

        // Streaming retry with reconnection support
        return flow {
            var attempt = 0
            var lastError: Throwable? = null

            while (attempt < config.maxAttempts) {
                try {
                    delegate.executeStreaming(prompt, model)
                        .catch { error ->
                            if (shouldRetry(error) && attempt < config.maxAttempts - 1) {
                                throw StreamRetrySignal(error)
                            } else {
                                throw error
                            }
                        }
                        .collect { chunk ->
                            emit(chunk)
                        }
                    return@flow // Success
                } catch (e: StreamRetrySignal) {
                    lastError = e.cause
                    attempt++

                    if (attempt < config.maxAttempts) {
                        val delay = calculateDelay(attempt - 1)
                        logger.warn {
                            "Stream interrupted (attempt $attempt/${config.maxAttempts}). " +
                                "Retrying in ${delay.inWholeMilliseconds}ms. Error: ${e.cause.message}"
                        }
                        delay(delay)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    if (!shouldRetry(e)) throw e

                    lastError = e
                    attempt++

                    if (attempt >= config.maxAttempts) throw e

                    val delay = calculateDelay(attempt - 1)
                    logger.warn {
                        "Stream failed (attempt $attempt/${config.maxAttempts}). " +
                            "Retrying in ${delay.inWholeMilliseconds}ms"
                    }
                    delay(delay)
                }
            }

            throw lastError ?: IllegalStateException("Stream retry exhausted")
        }
    }

    override suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<LLMChoice> = withRetry("executeMultipleChoices") {
        delegate.executeMultipleChoices(prompt, model, tools)
    }

    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel
    ): ModerationResult = withRetry("moderate") {
        delegate.moderate(prompt, model)
    }

    private suspend fun <T> withRetry(
        operation: String,
        block: suspend () -> T
    ): T {
        var lastException: Throwable? = null

        repeat(config.maxAttempts) { attempt ->
            try {
                return block()
            } catch (e: CancellationException) {
                throw e // Never retry cancellations
            } catch (e: Throwable) {
                lastException = e

                if (!shouldRetry(e) || attempt >= config.maxAttempts - 1) {
                    throw e
                }

                val delay = calculateDelay(attempt, e)
                logger.warn {
                    "$operation failed (attempt ${attempt + 1}/${config.maxAttempts}). " +
                        "Retrying in ${delay.inWholeMilliseconds}ms. Error: ${e.message}"
                }
                delay(delay)
            }
        }

        throw lastException!!
    }

    private fun shouldRetry(error: Throwable): Boolean {
        val message = error.message ?: return false

        // Check if error matches any retry pattern
        return config.retryablePatterns.any { pattern ->
            pattern.matches(message)
        }
    }

    private fun calculateDelay(attempt: Int, error: Throwable? = null): Duration {
        // Check for retry-after hint in error message
        error?.message?.let { message ->
            config.retryAfterExtractor?.extract(message)?.let { retryAfter ->
                return retryAfter
            }
        }

        // Exponential backoff with jitter
        var exponentialMs = config.initialDelay.inWholeMilliseconds.toDouble()
        repeat(attempt) {
            exponentialMs *= config.backoffMultiplier
        }
        val boundedMs = minOf(exponentialMs, config.maxDelay.inWholeMilliseconds.toDouble())

        // Add jitter
        val jitter = Random.nextDouble(1.0 - config.jitterFactor, 1.0 + config.jitterFactor)
        val finalMs = (boundedMs * jitter).toLong()

        return finalMs.milliseconds
    }

    private class StreamRetrySignal(override val cause: Throwable) : Exception(cause)
}
