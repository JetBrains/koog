package ai.koog.agents.features.tokenizer.feature

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.AIAgentGraphPipeline
import ai.koog.agents.core.feature.AIAgentNonGraphFeature
import ai.koog.agents.core.feature.AIAgentNonGraphPipeline
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.prompt.tokenizer.CachingTokenizer
import ai.koog.prompt.tokenizer.NoTokenizer
import ai.koog.prompt.tokenizer.OnDemandTokenizer
import ai.koog.prompt.tokenizer.PromptTokenizer
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
    public companion object Feature : AIAgentGraphFeature<MessageTokenizerConfig, MessageTokenizer>, AIAgentNonGraphFeature<MessageTokenizerConfig, MessageTokenizer> {

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
            pipeline: AIAgentGraphPipeline,
        ) {
            val promptTokenizer = if (config.enableCaching) {
                CachingTokenizer(config.tokenizer)
            } else {
                OnDemandTokenizer(config.tokenizer)
            }

            val feature = MessageTokenizer(promptTokenizer)

            pipeline.interceptContextAgentFeature(this) { feature }
        }

        override fun install(
            config: MessageTokenizerConfig,
            pipeline: AIAgentNonGraphPipeline
        ) {
            val promptTokenizer = if (config.enableCaching) {
                CachingTokenizer(config.tokenizer)
            } else {
                OnDemandTokenizer(config.tokenizer)
            }

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
public val AIAgentContext.tokenizer: PromptTokenizer get() = featureOrThrow(
    MessageTokenizer.Feature
).promptTokenizer
