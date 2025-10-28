package ai.koog.prompt.executor.clients.retry

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.model.LLMChoice
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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
    internal val config: RetryConfig = RetryConfig()
) : LLMClient {

    /**
     * Retrieves the configured instance of the `LLMProvider` in use.
     *
     * This method returns the `LLMProvider` instance associated with the client,
     * facilitating identification or interaction with the specific provider of
     * large language models (e.g., Google, OpenAI, Meta, etc.).
     *
     * @return the current `LLMProvider` associated with this client.
     */
    override fun llmProvider(): LLMProvider = delegate.llmProvider()

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

    // Streaming retry: Only retries connection failures before the first token is received.
    // Once streaming starts, errors are passed through to avoid content duplication.
    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): Flow<StreamFrame> =
        flow {
            repeat(config.maxAttempts) { attempt ->
                var firstFrameReceived = false
                try {
                    delegate.executeStreaming(prompt, model, tools).collect { chunk ->
                        firstFrameReceived = true
                        emit(chunk)
                    }
                    return@flow
                } catch (e: CancellationException) {
                    throw e // Never retry cancellations
                } catch (e: Throwable) {
                    // If we already received tokens, don't retry - pass error through
                    if (firstFrameReceived) {
                        throw e
                    }

                    if (!shouldRetry(e) || attempt >= config.maxAttempts - 1) {
                        throw e
                    }

                    val delay = calculateDelay(attempt, e)
                    logger.warn {
                        "Stream connection failed before first token (attempt ${attempt + 1}/${config.maxAttempts}). " +
                            "Retrying in ${delay.inWholeMilliseconds}ms. Error: ${e.message}"
                    }
                    delay(delay)
                }
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

        // Add jitter (only increases delay, never decreases)
        val jitterMs = Random.nextDouble(0.0, boundedMs * config.jitterFactor)
        val finalMs = (boundedMs + jitterMs).toLong()

        return finalMs.milliseconds
    }

    override fun close() {
        delegate.close()
    }
}

/**
 * Converts an instance of [LLMClient] into a retrying client with customizable retry behavior.
 *
 * @param retryConfig Configuration for retry behavior. Defaults to [RetryConfig.DEFAULT].
 * @return A new instance of [RetryingLLMClient] that adds retry logic to the provided client.
 */
public fun LLMClient.toRetryingClient(
    retryConfig: RetryConfig = RetryConfig.DEFAULT
): RetryingLLMClient =
    RetryingLLMClient(
        delegate = this,
        config = retryConfig
    )
