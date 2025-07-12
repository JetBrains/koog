package ai.koog.prompt.executor.clients.openai

import ai.koog.prompt.executor.clients.LLModelDefinitions
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

/**
 * Object containing a collection of predefined OpenAI model configurations.
 * These models span various use cases, including reasoning, chat, and cost-optimized tasks.
 *
 * Note: All models with vision (image) capabilities also support sending PDF files.
 *
 * | Name                                | Speed     | Price              | Input              | Output             |
 * |-------------------------------------|-----------|--------------------|--------------------|--------------------|
 * | [Reasoning.GPT4oMini]               | Medium    | $1.1-$4.4          | Text, Image, Tools | Text, Tools        |
 * | [Reasoning.O3Mini]                  | Medium    | $1.1-$4.4          | Text, Tools        | Text, Tools        |
 * | [Reasoning.O1Mini]                  | Slow      | $1.1-$4.4          | Text               | Text               |
 * | [Reasoning.O3]                      | Slowest   | $10-$40            | Text, Image, Tools | Text, Tools        |
 * | [Reasoning.O1]                      | Slowest   | $15-$60            | Text, Image, Tools | Text, Tools        |
 * | [Chat.GPT4o]                        | Medium    | $2.5-$10           | Text, Image, Tools | Text, Tools        |
 * | [Chat.GPT4_1]                       | Medium    | $2-$8              | Text, Image, Tools | Text, Tools        |
 * | [Audio.GPT4oMiniAudio]              | Fast      | $0.15-$0.6/$10-$20 | Text, Audio, Tools | Text, Audio, Tools |
 * | [Audio.GPT4oAudio]                  | Medium    | $2.5-$10/$40-$80   | Text, Audio, Tools | Text, Audio, Tools |
 * | [CostOptimized.O4Mini]              | Medium    | $1.1-$4.4          | Text, Image, Tools | Text, Tools        |
 * | [CostOptimized.GPT4_1Nano]          | Very fast | $0.1-$0.4          | Text, Image, Tools | Text, Tools        |
 * | [CostOptimized.GPT4_1Mini]          | Fast      | $0.4-$1.6          | Text, Image, Tools | Text, Tools        |
 * | [CostOptimized.GPT4oMini]           | Fast      | $0.15-$0.6         | Text, Image, Tools | Text, Tools        |
 * | [CostOptimized.O1Mini]              | Slow      | $1.1-$4.4          | Text               | Text               |
 * | [CostOptimized.O3Mini]              | Medium    | $1.1-$4.4          | Text, Tools        | Text, Tools        |
 * | [Embeddings.TextEmbedding3Small]    | Medium    | $0.02              | Text               | Text               |
 * | [Embeddings.TextEmbedding3Large]    | Slow      | $0.13              | Text               | Text               |
 * | [Embeddings.TextEmbeddingAda002]    | Slow      | $0.1               | Text               | Text               |
 * | [Moderation.Text]                   | Medium    | -                  | Text               | Moderation Result  |
 * | [Moderation.Omni]                   | Medium    | $4.40              | Text               | Moderation Result  |
 */
public object OpenAIModels : LLModelDefinitions {
    // TODO: support thinking tokens
    /**
     * Object containing moderation models designed to detect harmful content in text and images.
     * These models are free to use and can identify various categories of potentially harmful content.
     */
    public object Moderation {
        /**
         * Omni-moderation is the most capable moderation model, accepting both text and images as input.
         * It can identify potentially harmful content across multiple categories.
         *
         * Performance: High
         * Speed: Medium
         * Input: Text, image
         * Output: Text
         */
        public val Omni: LLModel = LLModel(
            provider = LLMProvider.OpenAI, id = "omni-moderation-latest", capabilities = listOf(
                LLMCapability.Moderation, LLMCapability.Vision.Image
            )
        )

        /**
         * Text-moderation is a previous generation text-only moderation model.
         * It can identify potentially harmful content in text across multiple categories.
         *
         * Performance: Average
         * Speed: Medium
         * Input: Text
         * Output: Text
         *
         * 32,768 max output tokens
         * Sep 01, 2021 knowledge cutoff
         */
        public val Text: LLModel = LLModel(
            provider = LLMProvider.OpenAI, id = "text-moderation-latest", capabilities = listOf(
                LLMCapability.Moderation
            )
        )
    }

    /**
     * Object containing a set of pre-configured reasoning models with various capabilities and constraints.
     * These models are designed for tasks ranging from general reasoning to domain-specific applications,
     * supporting key features like multi-step problem solving, structured outputs, and context-based responses.
     */
    public object Reasoning {
        /**
         * GPT-4o mini is a smaller, more affordable version of GPT-4o that maintains high quality while being
         * more cost-effective. It's designed for tasks that don't require the full capabilities of GPT-4o.
         *
         * 128K context window.
         * 16,384 max output tokens
         * Oct 01, 2023 knowledge cutoff
         *
         * @see <a href="https://platform.openai.com/docs/models/gpt-4o-mini">
         */
        public val GPT4oMini: LLModel = LLModel(
            provider = LLMProvider.OpenAI, id = "gpt-4o-mini", capabilities = listOf(
                LLMCapability.Temperature, LLMCapability.Schema.JSON.Full, LLMCapability.Speculation,
                LLMCapability.Tools, LLMCapability.ToolChoice, LLMCapability.Vision.Image, LLMCapability.Completion,
                LLMCapability.MultipleChoices
            )
        )

        /**
         * o3-mini is a smaller, more affordable version of o3. It's a small reasoning model,
         * providing high intelligence at the same cost and latency targets of o1-mini.
         * o3-mini supports key developer features, like Structured Outputs, function calling,
         * and Batch API.
         *
         * 200,000 context window
         * 100,000 max output tokens
         * Oct 01, 2023 knowledge cutoff
         * Reasoning token support
         *
         * @see <a href="https://platform.openai.com/docs/models/o3-mini">
         */
        public val O3Mini: LLModel = LLModel(
            provider = LLMProvider.OpenAI, id = "o3-mini", capabilities = listOf(
                LLMCapability.Tools, LLMCapability.ToolChoice, LLMCapability.Speculation,
                LLMCapability.Schema.JSON.Full, LLMCapability.Completion, LLMCapability.MultipleChoices
            )
        )

        /**
         * o1-mini is designed to solve hard problems across domains.
         * o1-mini is a faster and more affordable reasoning model,
         * but we recommend using the newer o3-mini model that features higher intelligence
         * at the same latency and price as o1-mini.
         *
         * 128,000 context window
         * 65,536 max output tokens
         * Oct 01, 2023 knowledge cutoff
         * Reasoning token support
         *
         * @see <a href="https://platform.openai.com/docs/models/o1-mini">
         */
        public val O1Mini: LLModel = LLModel(
            provider = LLMProvider.OpenAI, id = "o1-mini", capabilities = listOf(
                LLMCapability.Speculation, LLMCapability.Schema.JSON.Full, LLMCapability.Completion,
                LLMCapability.MultipleChoices
            )
        )

        /**
         * o3 is a well-rounded and powerful model across domains.
         * It is capable of math, science, coding, and visual reasoning tasks.
         * It also excels at technical writing and instruction-following.
         * Use it to think through multi-step problems that involve analysis across text, code, and images.
         *
         * 200,000 context window
         * 100,000 max output tokens
         * Jun 01, 2024 knowledge cutoff
         * Reasoning token support
         *
         * @see <a href="https://platform.openai.com/docs/models/o3">
         */
        public val O3: LLModel = LLModel(
            provider = LLMProvider.OpenAI, id = "o3", capabilities = listOf(
                LLMCapability.Tools, LLMCapability.ToolChoice, LLMCapability.Speculation,
                LLMCapability.Schema.JSON.Full, LLMCapability.Vision.Image, LLMCapability.Completion,
                LLMCapability.MultipleChoices
            )
        )

        /**
         * The o1 series of models are trained with reinforcement learning to perform
         * complex reasoning. o1 models think before they answer,
         * producing a long internal chain of thought before responding to the user.
         *
         * 200,000 context window
         * 100,000 max output tokens
         * Oct 01, 2023 knowledge cutoff
         * Reasoning token support
         *
         * @see <a href="https://platform.openai.com/docs/models/o1">
         */
        public val O1: LLModel = LLModel(
            provider = LLMProvider.OpenAI, id = "o1", capabilities = listOf(
                LLMCapability.Tools, LLMCapability.ToolChoice, LLMCapability.Speculation,
                LLMCapability.Schema.JSON.Full, LLMCapability.Vision.Image, LLMCapability.Completion,
                LLMCapability.MultipleChoices
            )
        )
    }

    /**
     * Object that provides pre-configured instances of advanced GPT models for different use cases.
     * These instances represent versatile and high-performance large language models capable of handling various tasks like
     * text completion, image input processing, structured outputs, and interfacing with tools.
     */
    public object Chat {
        /**
         * GGPT-4o (“o” for “omni”) is a versatile, high-intelligence flagship model.
         * It accepts both text and image inputs, and produces text outputs (including Structured Outputs).
         * It is the best model for most tasks, and is currently the most capable model
         * outside of the o-series models.
         *
         * 128,000 context window
         * 16,384 max output tokens
         * Oct 01, 2023 knowledge cutoff
         *
         *
         * @see <a href="https://platform.openai.com/docs/models/gpt-4o">
         */
        public val GPT4o: LLModel = LLModel(
            provider = LLMProvider.OpenAI, id = "gpt-4o", capabilities = listOf(
                LLMCapability.Temperature, LLMCapability.ToolChoice, LLMCapability.Schema.JSON.Full,
                LLMCapability.Speculation, LLMCapability.Tools, LLMCapability.Vision.Image, LLMCapability.Completion,
                LLMCapability.MultipleChoices
            )
        )

        /**
         * GPT-4.1 is a flagship model for complex tasks.
         * It is well suited for problem solving across domains.
         *
         * 1,047,576 context window
         * 32,768 max output tokens
         * Jun 01, 2024 knowledge cutoff
         *
         *
         * @see <a href="https://platform.openai.com/docs/models/gpt-4.1">
         */
        public val GPT4_1: LLModel = LLModel(
            provider = LLMProvider.OpenAI, id = "gpt-4.1", capabilities = listOf(
                LLMCapability.Temperature, LLMCapability.Schema.JSON.Full, LLMCapability.Speculation,
                LLMCapability.Tools, LLMCapability.ToolChoice, LLMCapability.Vision.Image, LLMCapability.Completion,
                LLMCapability.MultipleChoices
            )
        )
    }

    /**
     * The `Audio` object provides access to preconfigured audio-enabled Large Language Models (LLMs).
     * These models support both audio input and output functionalities, making them suitable
     * for various audio-centric applications.
     */
    public object Audio {
        /**
         * GPT-4o mini Audio is a smaller,
         * more affordable version of `GPT-4o Audio` that maintains high quality while being more cost-effective.
         * It's designed to input audio or create audio outputs.
         *
         * 128,000 context window
         * 16,384 max output tokens
         * Oct 01, 2023 knowledge cutoff
         *
         * @see <a href="https://platform.openai.com/docs/models/gpt-4o-mini-audio-preview">
         */
        public val GPT4oMiniAudio: LLModel = LLModel(
            provider = LLMProvider.OpenAI, id = "gpt-4o-mini-audio-preview", capabilities = listOf(
                LLMCapability.Temperature, LLMCapability.Completion, LLMCapability.Tools, LLMCapability.ToolChoice,
                LLMCapability.Audio
            )
        )

        /**
         * GPT-4o Audio is a model designed to input audio or create audio outputs.
         *
         * 128,000 context window
         * 16,384 max output tokens
         * Oct 01, 2023 knowledge cutoff
         *
         * @see <a href="https://platform.openai.com/docs/models/gpt-4o-audio-preview">
         */
        public val GPT4oAudio: LLModel = LLModel(
            provider = LLMProvider.OpenAI, id = "gpt-4o-audio-preview", capabilities = listOf(
                LLMCapability.Temperature, LLMCapability.Completion, LLMCapability.Tools, LLMCapability.ToolChoice,
                LLMCapability.Audio
            )
        )
    }

    /**
     * The `CostOptimized` object provides a collection of cost-efficient language models
     * optimized for specific use cases. These models aim to balance quality, performance,
     * and affordability, catering to various tasks like reasoning, speculation, and tools integration.
     */
    public object CostOptimized {
        /**
         * o4-mini is a smaller, more affordable version of o4 that maintains high quality while being
         * more cost-effective. It's optimized for fast, effective reasoning with exceptionally efficient
         * performance in coding and visual tasks..
         *
         * 200,000 context window
         * 100,000 max output tokens
         * Jun 01, 2024 knowledge cutoff
         * Reasoning token support
         *
         *
         * @see <a href="https://platform.openai.com/docs/models/o4-mini">
         */
        public val O4Mini: LLModel = LLModel(
            provider = LLMProvider.OpenAI, id = "o4-mini", capabilities = listOf(
                LLMCapability.Schema.JSON.Full, LLMCapability.Speculation,
                LLMCapability.Tools, LLMCapability.ToolChoice, LLMCapability.Vision.Image, LLMCapability.Completion,
                LLMCapability.MultipleChoices
            )
        )

        /**
         * GPT-4.1-nano is the smallest and most affordable model in the GPT-4.1 family.
         * It's designed for tasks that require basic capabilities at the lowest possible cost.
         *
         * 1,047,576 context window
         * 32,768 max output tokens
         * Jun 01, 2024 knowledge cutoff
         *
         * @see <a href="https://platform.openai.com/docs/models/gpt-4.1-nano">
         */
        public val GPT4_1Nano: LLModel = LLModel(
            provider = LLMProvider.OpenAI, id = "gpt-4.1-nano", capabilities = listOf(
                LLMCapability.Temperature, LLMCapability.Schema.JSON.Full, LLMCapability.Speculation,
                LLMCapability.Tools, LLMCapability.ToolChoice, LLMCapability.Vision.Image, LLMCapability.Completion,
                LLMCapability.MultipleChoices
            )
        )

        /**
         * GPT-4.1 mini provides a balance between intelligence, speed,
         * and cost that makes it an attractive model for many use cases.
         *
         * 1,047,576 context window
         * 32,768 max output tokens
         * Jun 01, 2024 knowledge cutoff
         *
         *
         * @see <a href="https://platform.openai.com/docs/models/gpt-4.1-mini">
         */
        public val GPT4_1Mini: LLModel = LLModel(
            provider = LLMProvider.OpenAI, id = "gpt-4.1-mini", capabilities = listOf(
                LLMCapability.Temperature, LLMCapability.Schema.JSON.Full, LLMCapability.Speculation,
                LLMCapability.Tools, LLMCapability.ToolChoice, LLMCapability.Vision.Image, LLMCapability.Completion,
                LLMCapability.MultipleChoices
            )
        )

        /**
         * See [Reasoning.GPT4oMini]
         */
        public val GPT4oMini: LLModel get() = Reasoning.GPT4oMini

        /**
         * See [Reasoning.O1Mini]
         */
        public val O1Mini: LLModel get() = Reasoning.O1Mini

        /**
         * See [Reasoning.O3Mini]
         */
        public val O3Mini: LLModel get() = Reasoning.O3Mini
    }

    /**
     * Embeddings are useful for search, clustering, recommendations, anomaly detection, and classification tasks.
     *
     * It is NOT recommended to use these models for other tasks other than for computing embeddings.
     * */
    public object Embeddings {
        /**
         * text-embedding-3-small is an improved, more performant version of the ada embedding model.
         *
         * Smaller, faster, and more cost-effective than ada-002, with better quality.
         *
         * Outputs: 1536-dimensional vectors (or 512-dimensional vectors, optionally)
         * Max input tokens: 8191
         * Released: Jan 2024
         *
         * Embeddings are a numerical representation of text that can be used to measure
         * the relatedness between two pieces of text.
         * Embeddings are useful for search, clustering, recommendations,
         * anomaly detection, and classification tasks.
         *
         * @see <a href="https://platform.openai.com/docs/models/text-embedding-3-small">
         */
        public val TextEmbedding3Small: LLModel = LLModel(
            provider = LLMProvider.OpenAI, id = "text-embedding-3-small", capabilities = listOf(
                LLMCapability.Schema.JSON.Full, LLMCapability.Embed
            )
        )

        /**
         * text-embedding-3-large is currently the most capable embedding model
         * for both english and non-english tasks.
         *
         * Currently provides best embedding quality;
         * supports multiple dimensions for flexibility and cost tradeoff.
         *
         *
         * Outputs: 3072-dimensional vectors (or 256-/512-/1024-dimensional vectors, optionally)
         * Max input tokens: 8191
         * Released: Jan 2024
         *
         * Embeddings are a numerical representation of text that can be used to
         * measure the relatedness between two pieces of text. Embeddings are useful for search,
         * clustering, recommendations, anomaly detection, and classification tasks.
         *
         *
         * @see <a href="https://platform.openai.com/docs/models/text-embedding-3-large">
         */
        public val TextEmbedding3Large: LLModel = LLModel(
            provider = LLMProvider.OpenAI, id = "text-embedding-3-large", capabilities = listOf(
                LLMCapability.Schema.JSON.Full, LLMCapability.Embed
            )
        )

        /**
         * text-embedding-ada-002 is more performant version of the initial ada embedding model. But it's an older
         * model compared to [TextEmbedding3Small] and [TextEmbedding3Large].
         *
         * Fast, cheap, good for many general tasks
         *
         * Outputs: 1536-dimensional vectors
         * Max input tokens: 8191
         * Released: Dec 2022
         *
         *
         * Embeddings are a numerical representation of text that can be used to measure the relatedness
         * between two pieces of text. Embeddings are useful for search, clustering, recommendations,
         * anomaly detection, and classification tasks.
         *
         * @see <a href="https://platform.openai.com/docs/models/text-embedding-ada-002">
         */
        public val TextEmbeddingAda002: LLModel = LLModel(
            provider = LLMProvider.OpenAI, id = "text-embedding-ada-002", capabilities = listOf(
                LLMCapability.Schema.JSON.Full, LLMCapability.Embed
            )
        )
    }
}
