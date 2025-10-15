package ai.koog.agents.features.tokenizer.feature

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.featureOrThrow
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.feature.AIAgentFunctionalFeature
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.pipeline.AIAgentFunctionalPipeline
import ai.koog.agents.core.feature.pipeline.AIAgentGraphPipeline
import ai.koog.prompt.tokenizer.CachingTokenizer
import ai.koog.prompt.tokenizer.NoTokenizer
import ai.koog.prompt.tokenizer.OnDemandTokenizer
import ai.koog.prompt.tokenizer.PromptTokenizer
import ai.koog.prompt.tokenizer.Tokenizer

/**
 * Configuration class for message tokenization settings.
 */
public class MessageTokenizerConfig : FeatureConfig() {
    /**
     * The `tokenizer` property determines the strategy used for tokenizing text
     * and estimating token counts within a message-processing feature.
     */
    public var tokenizer: Tokenizer = NoTokenizer()

    /**
     * Indicates whether caching is enabled for tokenization processes.
     *
     * When set to `true`, a caching tokenizer will be used to optimize performance by
     * caching tokenization results. If `false`, an on-demand tokenizer will be utilized,
     * which performs tokenization as needed without caching.
     */
    public var enableCaching: Boolean = true
}

/**
 * The [MessageTokenizer] feature is responsible for handling tokenization of messages using a provided [Tokenizer]
 * implementation.
 *
 * @property promptTokenizer An instance of [PromptTokenizer] used to process tokenization of messages and prompts.
 */
public class MessageTokenizer(public val promptTokenizer: PromptTokenizer) {
    /**
     * Companion object implementing agent feature, handling [MessageTokenizer] creation and installation.
     */
    public companion object Feature :
        AIAgentGraphFeature<MessageTokenizerConfig, MessageTokenizer>,
        AIAgentFunctionalFeature<MessageTokenizerConfig, MessageTokenizer> {

        override val key: AIAgentStorageKey<MessageTokenizer> =
            AIAgentStorageKey("agents-features-tracing")

        override fun createInitialConfig(): MessageTokenizerConfig = MessageTokenizerConfig()

        /**
         * Creates a feature implementation using the provided configuration.
         */
        private fun createFeature(config: MessageTokenizerConfig): MessageTokenizer {
            val promptTokenizer = if (config.enableCaching) {
                CachingTokenizer(config.tokenizer)
            } else {
                OnDemandTokenizer(config.tokenizer)
            }

            return MessageTokenizer(promptTokenizer)
        }

        override fun install(
            config: MessageTokenizerConfig,
            pipeline: AIAgentGraphPipeline,
        ): MessageTokenizer {
            return createFeature(config)
        }

        override fun install(
            config: MessageTokenizerConfig,
            pipeline: AIAgentFunctionalPipeline,
        ): MessageTokenizer {
            return createFeature(config)
        }
    }
}

/**
 * Provides access to the [PromptTokenizer] instance provided by the [MessageTokenizer.Feature] and
 * used within the AI agent's context.
 */
public fun AIAgentContext.tokenizer(): PromptTokenizer =
    featureOrThrow(MessageTokenizer.Feature).promptTokenizer
