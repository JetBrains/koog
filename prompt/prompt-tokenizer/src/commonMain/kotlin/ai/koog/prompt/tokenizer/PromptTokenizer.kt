package ai.koog.prompt.tokenizer

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.message.Message
import kotlin.collections.sumOf

/**
 * An interface that provides utilities for tokenizing and calculating token usage in messages and prompts.
 */
public interface PromptTokenizer {
    /**
     * Calculates the number of tokens required for a given message.
     *
     * @param message The message for which the token count should be determined.
     * @return The number of tokens required to encode the message.
     */
    public fun tokenCountFor(message: Message): Int

    /**
     * Calculates the total number of tokens spent in a given prompt.
     *
     * @param prompt The prompt for which the total tokens spent need to be calculated.
     * @return The total number of tokens spent as an integer.
     */
    public fun tokenCountFor(prompt: Prompt): Int
}

/**
 * An implementation of the [PromptTokenizer] interface that delegates token counting
 * to an instance of the [Tokenizer] interface. The class provides methods to estimate
 * the token count for individual messages and for the entirety of a prompt.
 *
 * This is useful in contexts where token-based costs or limitations are significant,
 * such as when interacting with large language models (LLMs).
 *
 * @property tokenizer The [Tokenizer] instance used for token counting.
 */
public class OnDemandTokenizer(private val tokenizer: Tokenizer) : PromptTokenizer {

    /**
     * Computes the number of tokens in a given message.
     *
     * @param message The message for which the token count needs to be calculated.
     *                The content of the message is analyzed to estimate the token count.
     * @return The estimated number of tokens in the message content.
     */
    public override fun tokenCountFor(message: Message): Int = tokenizer.countTokens(message.content)

    /**
     * Calculates the total number of tokens spent for the given prompt based on its messages.
     *
     * @param prompt The `Prompt` instance containing the list of messages for which the total token count will be calculated.
     * @return The total number of tokens across all messages in the prompt.
     */
    public override fun tokenCountFor(prompt: Prompt): Int = prompt.messages.sumOf(::tokenCountFor)
}

/**
 * A caching implementation of the `PromptTokenizer` interface that optimizes token counting
 * by storing previously computed token counts for messages. This reduces redundant computations
 * when the same message is processed multiple times.
 *
 * @constructor Creates an instance of `CachingTokenizer` with a provided `Tokenizer` instance
 * that performs the actual token counting.
 * @property tokenizer The underlying `Tokenizer` used for counting tokens in the message content.
 */
public class CachingTokenizer(private val tokenizer: Tokenizer) : PromptTokenizer {
    /**
     * A cache that maps a `Message` to its corresponding token count.
     *
     * This is used to store the results of token computations for reuse, optimizing performance
     * by avoiding repeated invocations of the token counting process on the same message content.
     *
     * Token counts are computed lazily and stored in the cache when requested via the `tokensFor`
     * method. This cache can be cleared using the `clearCache` method.
     */
    internal val cache = mutableMapOf<Message, Int>()

    /**
     * Retrieves the number of tokens contained in the content of the given message.
     * This method utilizes caching to improve performance, storing previously
     * computed token counts and reusing them for identical messages.
     *
     * @param message The message whose content's token count is to be retrieved
     * @return The number of tokens in the content of the message
     */
    public override fun tokenCountFor(message: Message): Int = cache.getOrPut(message) {
        tokenizer.countTokens(message.content)
    }

    /**
     * Calculates the total number of tokens spent on the given prompt by summing the token usage
     * of all messages associated with the prompt.
     *
     * @param prompt The prompt containing the list of messages whose token usage will be calculated.
     * @return The total number of tokens spent across all messages in the provided prompt.
     */
    public override fun tokenCountFor(prompt: Prompt): Int = prompt.messages.sumOf(::tokenCountFor)

    /**
     * Clears all cached token counts from the internal cache.
     *
     * This method is useful when the state of the cached data becomes invalid
     * or needs resetting. After calling this, any subsequent token count
     * calculations will be recomputed rather than retrieved from the cache.
     */
    public fun clearCache() {
        cache.clear()
    }
}
