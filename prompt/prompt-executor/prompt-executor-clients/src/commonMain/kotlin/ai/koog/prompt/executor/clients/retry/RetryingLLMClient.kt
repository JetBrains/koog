package ai.koog.prompt.executor.clients.retry

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.http.client.KoogHttpClientException
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.IncompleteStreamException
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.structure.json.generator.BasicJsonSchemaGenerator
import ai.koog.prompt.structure.json.generator.StandardJsonSchemaGenerator
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.jvm.JvmOverloads
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
public class RetryingLLMClient @JvmOverloads constructor(
    private val delegate: LLMClient,
    internal val config: RetryConfig = RetryConfig()
) : LLMClient() {

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

    override suspend fun models(): List<LLModel> = withRetry("models") {
        delegate.models()
    }

    /**
     * Embeds the given text, retrying on transient failures according to [config].
     *
     * @param text The text to embed.
     * @param model The model to use for embedding.
     * @return A list of floating-point values representing the embedding vector.
     */
    override suspend fun embed(
        text: String,
        model: LLModel
    ): List<Double> = withRetry("embed") {
        delegate.embed(text, model)
    }

    /**
     * Embeds the given inputs, retrying on transient failures according to [config].
     *
     * @param inputs The list of texts to embed.
     * @param model The model to use for embedding.
     * @return A list of embedding vectors, one per input string.
     */
    override suspend fun embed(
        inputs: List<String>,
        model: LLModel
    ): List<List<Double>> = withRetry("embed") {
        delegate.embed(inputs, model)
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
        if (error is IncompleteStreamException) return true

        // Also consult the wrapped cause: some clients re-throw as a domain wrapper whose
        // own message omits the retry-matching tokens (e.g. "LLM call failed") even though
        // the underlying KoogHttpClientException carries the status code and keywords.
        val messages = listOfNotNull(error.message, error.unwrapHttpException()?.message)
        if (messages.isEmpty()) return false

        return config.retryablePatterns.any { pattern ->
            messages.any { pattern.matches(it) }
        }
    }

    private fun calculateDelay(attempt: Int, error: Throwable? = null): Duration =
        retryAfterHint(error) ?: exponentialBackoffWithJitter(attempt)

    // Prefers the header-aware extractor overload when the throwable carries a
    // KoogHttpClientException (directly or wrapped one level deep) so structured response
    // metadata is available; falls back to the message-based overload otherwise.
    private fun retryAfterHint(error: Throwable?): Duration? {
        val extractor = config.retryAfterExtractor ?: return null
        if (error == null) return null
        val koogException = error.unwrapHttpException()
        return if (koogException != null) {
            extractor.extract(koogException)
        } else {
            error.message?.let { extractor.extract(it) }
        }
    }

    private fun exponentialBackoffWithJitter(attempt: Int): Duration {
        var exponentialMs = config.initialDelay.inWholeMilliseconds.toDouble()
        repeat(attempt) {
            exponentialMs *= config.backoffMultiplier
        }
        val boundedMs = minOf(exponentialMs, config.maxDelay.inWholeMilliseconds.toDouble())
        // Jitter is drawn from [0, bound) so it only increases the delay, never decreases it -
        // guards against clients that shorten their backoff under load and stampede retries.
        val jitterMs = Random.nextDouble(0.0, boundedMs * config.jitterFactor)
        return (boundedMs + jitterMs).toLong().milliseconds
    }

    override fun close() {
        delegate.close()
    }

    override fun getStandardJsonSchemaGenerator(): StandardJsonSchemaGenerator {
        return delegate.getStandardJsonSchemaGenerator()
    }

    override fun getBasicJsonSchemaGenerator(): BasicJsonSchemaGenerator {
        return delegate.getBasicJsonSchemaGenerator()
    }

    // Returns the throwable itself if it is a KoogHttpClientException, or its immediate cause
    // when it wraps one. Keeps `shouldRetry` and `calculateDelay` symmetric so a wrapper whose
    // own message is non-matching still has its underlying HTTP metadata consulted.
    private fun Throwable.unwrapHttpException(): KoogHttpClientException? =
        this as? KoogHttpClientException ?: this.cause as? KoogHttpClientException
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
