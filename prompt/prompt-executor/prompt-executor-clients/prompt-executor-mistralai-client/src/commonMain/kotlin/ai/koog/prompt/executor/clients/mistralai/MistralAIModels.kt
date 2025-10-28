package ai.koog.prompt.executor.clients.mistralai

import ai.koog.prompt.executor.clients.LLModelDefinitions
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

/**
 * Object containing a collection of predefined Mistral AI model configurations.
 * These models span various use cases including chat, reasoning, coding, vision, and audio tasks.
 *
 * Models are organized by functionality:
 * - [Chat]: General purpose conversation models
 * - [Embeddings]: Semantic representation models
 * - [Moderation]: Content safety models
 */
public object MistralAIModels : LLModelDefinitions {

    /**
     * Object containing general purpose chat models for conversations and various tasks.
     * Includes both premier and open-source models with different capabilities and sizes.
     */
    public object Chat {

        /**
         * Mistral Medium 3 - Frontier-class multimodal model (Premier)
         *
         * Released May 2025 with multimodal capabilities.
         *
         * 128k context window
         *
         * @see <a href="https://docs.mistral.ai/models/">Mistral AI Models</a>
         */
        public val MistralMedium31: LLModel = LLModel(
            provider = LLMProvider.MistralAI,
            id = "mistral-medium-latest",
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Completion,
                LLMCapability.Tools,
                LLMCapability.ToolChoice,
                LLMCapability.Schema.JSON.Basic,
                LLMCapability.Schema.JSON.Standard,
                LLMCapability.Vision.Image,
                LLMCapability.Document,
                LLMCapability.MultipleChoices
            ),
            contextLength = 128_000
        )

        /**
         * Mistral Large 2.1 - Top-tier large model for high-complexity tasks (Premier)
         *
         * Released November 2024. Most capable model for complex reasoning and tasks.
         *
         * 128k context window
         *
         * @see <a href="https://docs.mistral.ai/models/">Mistral AI Models</a>
         */
        public val MistralLarge21: LLModel = LLModel(
            provider = LLMProvider.MistralAI,
            id = "mistral-large-latest",
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Completion,
                LLMCapability.Tools,
                LLMCapability.ToolChoice,
                LLMCapability.Schema.JSON.Basic,
                LLMCapability.Schema.JSON.Standard,
                LLMCapability.MultipleChoices
            ),
            contextLength = 128_000
        )

        /**
         * Mistral Small 2 - Updated small model (Premier)
         *
         * Released September 2024. Efficient model for standard tasks.
         *
         * 32k context window
         *
         * @see <a href="https://docs.mistral.ai/models/">Mistral AI Models</a>
         */
        public val MistralSmall2: LLModel = LLModel(
            provider = LLMProvider.MistralAI,
            id = "mistral-small-latest",
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Completion,
                LLMCapability.Tools,
                LLMCapability.ToolChoice,
                LLMCapability.Schema.JSON.Basic,
                LLMCapability.MultipleChoices
            ),
            contextLength = 32_000
        )

        /**
         * Magistral Medium 1.2 - Frontier reasoning model with vision (Premier)
         *
         * Released September 2025. Advanced reasoning capabilities with vision support.
         *
         * 128k context window
         *
         * @see <a href="https://docs.mistral.ai/models/">Mistral AI Models</a>
         */
        public val MagistralMedium12: LLModel = LLModel(
            provider = LLMProvider.MistralAI,
            id = "magistral-medium-latest",
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Completion,
                LLMCapability.Tools,
                LLMCapability.ToolChoice,
                LLMCapability.Schema.JSON.Basic,
                LLMCapability.Schema.JSON.Standard,
                LLMCapability.Speculation,
                LLMCapability.Vision.Image,
                LLMCapability.Document,
                LLMCapability.MultipleChoices
            ),
            contextLength = 128_000
        )

        /**
         * Codestral 2508 - Cutting-edge coding model (Premier)
         *
         * Released July 2025. Specializes in low-latency tasks like fill-in-the-middle,
         * code correction, and test generation.
         *
         * 256k context window
         *
         * @see <a href="https://docs.mistral.ai/models/">Mistral AI Models</a>
         */
        public val Codestral: LLModel = LLModel(
            provider = LLMProvider.MistralAI,
            id = "codestral-latest",
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Completion,
                LLMCapability.Tools,
                LLMCapability.ToolChoice,
                LLMCapability.Schema.JSON.Basic
            ),
            contextLength = 256_000
        )

        /**
         * Devstral Medium - Enterprise coding model (Premier)
         *
         * Released July 2025. Excels at exploring codebases, editing multiple files,
         * and powering software engineering agents.
         *
         * 128k context window
         *
         * @see <a href="https://docs.mistral.ai/models/">Mistral AI Models</a>
         */
        public val DevstralMedium: LLModel = LLModel(
            provider = LLMProvider.MistralAI,
            id = "devstral-medium-latest",
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Completion,
                LLMCapability.Tools,
                LLMCapability.ToolChoice,
                LLMCapability.Schema.JSON.Basic,
                LLMCapability.Schema.JSON.Standard
            ),
            contextLength = 128_000
        )
    }

    /**
     * Object containing embedding models for semantic representation.
     * Used for search, clustering, recommendations, and similarity tasks.
     */
    public object Embeddings {
        /**
         * Mistral Embed - State-of-the-art text embedding model
         *
         * Released December 2023. Semantic representation for text extracts.
         *
         * 8k context window
         *
         * @see <a href="https://docs.mistral.ai/models/">Mistral AI Models</a>
         */
        public val MistralEmbed: LLModel = LLModel(
            provider = LLMProvider.MistralAI,
            id = "mistral-embed",
            capabilities = listOf(
                LLMCapability.Embed
            ),
            contextLength = 8_000
        )

        /**
         * Codestral Embed - Code embedding model
         *
         * Released May 2025. Semantic representation for code extracts.
         *
         * 8k context window
         *
         * @see <a href="https://docs.mistral.ai/models/">Mistral AI Models</a>
         */
        public val CodestralEmbed: LLModel = LLModel(
            provider = LLMProvider.MistralAI,
            id = "codestral-embed",
            capabilities = listOf(
                LLMCapability.Embed
            ),
            contextLength = 8_000
        )
    }

    /**
     * Object containing moderation models for content safety.
     * Used to detect harmful text content.
     */
    public object Moderation {
        /**
         * Mistral Moderation - Content safety moderation model
         *
         * Released November 2024. Detects harmful text content.
         *
         * 8k context window
         *
         * @see <a href="https://docs.mistral.ai/models/">Mistral AI Models</a>
         */
        public val MistralModeration: LLModel = LLModel(
            provider = LLMProvider.MistralAI,
            id = "mistral-moderation-2411",
            capabilities = listOf(
                LLMCapability.Moderation
            ),
            contextLength = 8_000
        )
    }
}
