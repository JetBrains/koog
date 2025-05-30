package ai.koog.agents.features.tokenizer.feature

import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.AIAgentPipeline
import ai.koog.agents.features.common.config.FeatureConfig
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.tokenizer.NoTokenizer
import ai.koog.prompt.tokenizer.Tokenizer

/**
 * Configuration class for message tokenization settings.
 *
 * This class specifies the tokenizer to be used and whether caching should be enabled for tokenization.
 * It extends the base `FeatureConfig` class, allowing it to be integrated within a feature-driven system.
 */
public class MessageTokenizerConfig : FeatureConfig() {
    /**
     * The `tokenizer` property determines the strategy used for tokenizing text
     * and estimating token counts within a message-processing feature.
     *
     * This property allows overriding the default tokenization behavior by
     * specifying a custom `Tokenizer` implementation. By default, the
     * `NoTokenizer` instance is used, which effectively disables token counting
     * by always returning zero.
     *
     * Tokenizers play a key role in scenarios involving large language models (LLMs),
     * where accurate token counting can be essential for understanding and managing
     * resource usage or request limits.
     */
    public var tokenizer: Tokenizer = NoTokenizer()

    /**
     * Indicates whether caching is enabled for tokenization processes.
     *
     * When set to `true`, a caching tokenizer will be used to optimize performance by
     * caching tokenization results. If `false`, an on-demand tokenizer will be utilized,
     * which performs tokenization as needed without caching.
     *
     * This property affects the tokenizer's behavior for processing text in scenarios
     * where tokenized data may be reused frequently, such as in prompt management or
     * text analysis pipelines.
     */
    public var enableCaching: Boolean = true
}

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

/**
 * The [MessageTokenizer] feature is responsible for handling tokenization of messages using a provided [Tokenizer]
 * implementation. It serves as a feature that can be installed into an `AIAgentPipeline`. The tokenizer behavior can be configured
 * with caching or on-demand tokenization based on the provided configuration.
 *
 * @property promptTokenizer An instance of `PromptTokenizer` used to process tokenization of messages and prompts.
 */
public class MessageTokenizer(public val promptTokenizer: PromptTokenizer) {
    /**
     * Companion object implementing the [AIAgentFeature] interface for the [MessageTokenizer] feature.
     * This feature integrates a message tokenizer into the agent pipeline, allowing for tokenization
     * of input messages. It supports both caching and non-caching tokenization strategies based on the configuration.
     */
    public companion object Feature : AIAgentFeature<MessageTokenizerConfig, MessageTokenizer> {

        /**
         * A unique storage key used to identify the `MessageTokenizer` feature within the agent's feature storage.
         * This key ensures that the `MessageTokenizer` instance can be retrieved or referenced
         * when required during the lifecycle or operation of the agent.
         */
        override val key: AIAgentStorageKey<MessageTokenizer> =
            AIAgentStorageKey("agents-features-tracing")

        /**
         * Creates and returns the initial configuration for the `MessageTokenizer` feature.
         *
         * @return A new instance of `MessageTokenizerConfig` containing the default configuration.
         */
        override fun createInitialConfig(): MessageTokenizerConfig = MessageTokenizerConfig()

        /**
         * Installs the MessageTokenizer feature into the given AI Agent pipeline.
         *
         * Configures and initializes the appropriate tokenizer (caching or on-demand)
         * based on the provided configuration, and registers the MessageTokenizer
         * feature into the pipeline.
         *
         * @param config The configuration used to customize the MessageTokenizer feature, including tokenizer settings and caching options.
         * @param pipeline The AI Agent pipeline where the MessageTokenizer feature will be installed.
         */
        override fun install(
            config: MessageTokenizerConfig,
            pipeline: AIAgentPipeline,
        ) {
            val promptTokenizer = if (config.enableCaching)
                CachingTokenizer(config.tokenizer)
            else
                OnDemandTokenizer(config.tokenizer)

            val feature = MessageTokenizer(promptTokenizer)

            pipeline.interceptContextAgentFeature(this) { feature }
        }
    }
}

/**
 * Provides access to the `PromptTokenizer` instance used within the AI agent's context.
 *
 * This property retrieves the tokenizer from the agent's storage using the `MessageTokenizer.Feature`,
 * which must be initialized in the pipeline's features. The `PromptTokenizer` allows
 * for tokenization operations on prompts and messages during the agent's execution.
 *
 * It facilitates operations such as calculating token counts for messages and prompts,
 * which are critical in managing and optimizing interactions with language models.
 *
 * Throws an exception if the `MessageTokenizer.Feature` is not available in the context.
 */
public val AIAgentContextBase.tokenizer: PromptTokenizer get() = featureOrThrow(MessageTokenizer.Feature).promptTokenizer
