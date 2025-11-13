package ai.koog.prompt.executor.clients.openai

import ai.koog.prompt.executor.clients.LLModelDefinitions
import ai.koog.prompt.executor.clients.openai.OpenAIModels.Embeddings.TextEmbedding3Large
import ai.koog.prompt.executor.clients.openai.OpenAIModels.Embeddings.TextEmbedding3Small
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

/**
 * Object containing a collection of predefined OpenAI model configurations.
 * These models span various use cases, including reasoning, chat, and cost-optimized tasks.
 *
 * Note: All models with vision (image) capabilities also support sending PDF files.
 *
 * @see <a href="https://platform.openai.com/docs/models">Models list</a>
 *
 * | Name                             | Speed     | Price              | Input                        | Output             |
 * |----------------------------------|-----------|--------------------|------------------------------|--------------------|
 * | [Reasoning.O4Mini]               | Medium    | $1.1-$4.4          | Text, Image, Tools, Document | Text, Tools        |
 * | [Reasoning.O3Mini]               | Medium    | $1.1-$4.4          | Text, Tools                  | Text, Tools        |
 * | [Reasoning.O3]                   | Slowest   | $10-$40            | Text, Image, Tools, Document | Text, Tools        |
 * | [Reasoning.O1]                   | Slowest   | $15-$60            | Text, Image, Tools, Document | Text, Tools        |
 * | [Reasoning.GPT5Pro]              | Slowest   | $15-$120           | Text, Image, Tools, Document | Text, Tools        |
 * | [Chat.GPT4o]                     | Medium    | $2.5-$10           | Text, Image, Tools, Document | Text, Tools        |
 * | [Chat.GPT4_1]                    | Medium    | $2-$8              | Text, Image, Tools, Document | Text, Tools        |
 * | [Chat.GPT5]                      | Medium    | $1.25-$10          | Text, Image, Tools, Document | Text, Tools        |
 * | [Chat.GPT5Mini]                  | Fast      | $0.25-$2           | Text, Image, Tools, Document | Text, Tools        |
 * | [Chat.GPT5Nano]                  | Very fast | $0.05-$0.4         | Text, Image, Tools, Document | Text, Tools        |
 * | [Chat.GPT5Codex]                 | Medium    | $1.25-$10          | Text, Image, Tools, Document | Text, Tools        |
 * | [Audio.GptAudio]                 | Fast      | $2.5-$10           | Text, Audio, Tools           | Text, Audio, Tools |
 * | [Audio.GPT4oMiniAudio]           | Fast      | $0.15-$0.6/$10-$20 | Text, Audio, Tools           | Text, Audio, Tools |
 * | [Audio.GPT4oAudio]               | Medium    | $2.5-$10/$40-$80   | Text, Audio, Tools           | Text, Audio, Tools |
 * | [CostOptimized.O4Mini]           | Medium    | $1.1-$4.4          | Text, Image, Tools, Document | Text, Tools        |
 * | [CostOptimized.GPT4_1Nano]       | Very fast | $0.1-$0.4          | Text, Image, Tools, Document | Text, Tools        |
 * | [CostOptimized.GPT4_1Mini]       | Fast      | $0.4-$1.6          | Text, Image, Tools, Document | Text, Tools        |
 * | [CostOptimized.GPT4oMini]        | Fast      | $0.15-$0.6         | Text, Image, Tools           | Text, Tools        |
 * | [CostOptimized.O3Mini]           | Medium    | $1.1-$4.4          | Text, Tools                  | Text, Tools        |
 * | [Embeddings.TextEmbedding3Small] | Medium    | $0.02              | Text                         | Text               |
 * | [Embeddings.TextEmbedding3Large] | Slow      | $0.13              | Text                         | Text               |
 * | [Embeddings.TextEmbeddingAda002] | Slow      | $0.1               | Text                         | Text               |
 * | [Moderation.Omni]                | Medium    | $4.40              | Text                         | Moderation Result  |
 *
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
            provider = LLMProvider.OpenAI,
            id = "omni-moderation-latest",
            capabilities = listOf(
                LLMCapability.Moderation,
                LLMCapability.Vision.Image
            ),
            contextLength = 32_768,
        )
    }

    /**
     * Object containing a set of pre-configured reasoning models with various capabilities and constraints.
     * These models are designed for tasks ranging from general reasoning to domain-specific applications,
     * supporting key features like multi-step problem solving, structured outputs, and context-based responses.
     */
    public object Reasoning {
        /**
         * o4-mini is a smaller, more affordable version of o4 that maintains high quality while being
         * more cost-effective. It's optimized for fast, effective reasoning with exceptionally efficient
         * performance in coding and visual tasks.
         *
         * 200,000 context window
         * 100,000 max output tokens
         * Jun 01, 2024 knowledge cutoff
         * Reasoning token support
         *
         *
         * @see <a href="https://platform.openai.com/docs/models/o4-mini">Model page</a>
         */
        public val O4Mini: LLModel = LLModel(
            provider = LLMProvider.OpenAI,
            id = "o4-mini",
            capabilities = listOf(
                LLMCapability.Schema.JSON.Basic,
                LLMCapability.Schema.JSON.Standard,
                LLMCapability.Speculation,
                LLMCapability.Tools,
                LLMCapability.ToolChoice,
                LLMCapability.Vision.Image,
                LLMCapability.Document,
                LLMCapability.Completion,
                LLMCapability.MultipleChoices,
                LLMCapability.OpenAIEndpoint.Completions,
                LLMCapability.OpenAIEndpoint.Responses,
            ),
            contextLength = 200_000,
            maxOutputTokens = 100_000,
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
         * @see <a href="https://platform.openai.com/docs/models/o3-mini">Model page</a>
         */
        public val O3Mini: LLModel = LLModel(
            provider = LLMProvider.OpenAI,
            id = "o3-mini",
            capabilities = listOf(
                LLMCapability.Tools,
                LLMCapability.ToolChoice,
                LLMCapability.Speculation,
                LLMCapability.Schema.JSON.Basic,
                LLMCapability.Schema.JSON.Standard,
                LLMCapability.Completion,
                LLMCapability.MultipleChoices,
                LLMCapability.OpenAIEndpoint.Completions,
                LLMCapability.OpenAIEndpoint.Responses,
            ),
            contextLength = 200_000,
            maxOutputTokens = 100_000,
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
         * @see <a href="https://platform.openai.com/docs/models/o3">Model page</a>
         */
        public val O3: LLModel = LLModel(
            provider = LLMProvider.OpenAI,
            id = "o3",
            capabilities = listOf(
                LLMCapability.Tools,
                LLMCapability.ToolChoice,
                LLMCapability.Speculation,
                LLMCapability.Schema.JSON.Basic,
                LLMCapability.Schema.JSON.Standard,
                LLMCapability.Vision.Image,
                LLMCapability.Document,
                LLMCapability.Completion,
                LLMCapability.MultipleChoices,
                LLMCapability.OpenAIEndpoint.Completions,
                LLMCapability.OpenAIEndpoint.Responses,
            ),
            contextLength = 200_000,
            maxOutputTokens = 100_000,
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
         * @see <a href="https://platform.openai.com/docs/models/o1">Model page</a>
         */
        public val O1: LLModel = LLModel(
            provider = LLMProvider.OpenAI,
            id = "o1",
            capabilities = listOf(
                LLMCapability.Tools,
                LLMCapability.ToolChoice,
                LLMCapability.Speculation,
                LLMCapability.Schema.JSON.Basic,
                LLMCapability.Schema.JSON.Standard,
                LLMCapability.Vision.Image,
                LLMCapability.Document,
                LLMCapability.Completion,
                LLMCapability.MultipleChoices,
                LLMCapability.OpenAIEndpoint.Completions,
                LLMCapability.OpenAIEndpoint.Responses,
            ),
            contextLength = 200_000,
            maxOutputTokens = 100_000,
        )

        /**
         * GPT-5 pro uses more compute to think harder and provide consistently better answers.
         * GPT-5 pro is available in the Responses API only to enable support for multi-turn model interactions
         * before responding to API requests, and other advanced API features in the future.
         * As the most advanced reasoning model, GPT-5 pro defaults to (and only supports) reasoning.effort: high.
         * GPT-5 pro does not support code interpreter.
         *
         * 400,000 context window
         * 272,000 max output tokens
         * Sep 30, 2024 knowledge cutoff
         * Reasoning token support
         *
         * @see <a href="https://platform.openai.com/docs/models/gpt-5-pro">Model page</a>
         */
        public val GPT5Pro: LLModel = LLModel(
            provider = LLMProvider.OpenAI,
            id = "gpt-5-pro",
            capabilities = listOf(
                LLMCapability.Tools,
                LLMCapability.ToolChoice,
                LLMCapability.Speculation,
                LLMCapability.Schema.JSON.Basic,
                LLMCapability.Schema.JSON.Standard,
                LLMCapability.Vision.Image,
                LLMCapability.MultipleChoices,
                LLMCapability.OpenAIEndpoint.Responses,
            ),
            contextLength = 400_000,
            maxOutputTokens = 272_000,
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
         * @see <a href="https://platform.openai.com/docs/models/gpt-4o">Model page</a>
         */
        public val GPT4o: LLModel = LLModel(
            provider = LLMProvider.OpenAI,
            id = "gpt-4o",
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.ToolChoice,
                LLMCapability.Schema.JSON.Basic,
                LLMCapability.Schema.JSON.Standard,
                LLMCapability.Speculation,
                LLMCapability.Tools,
                LLMCapability.Vision.Image,
                LLMCapability.Document,
                LLMCapability.Completion,
                LLMCapability.MultipleChoices,
                LLMCapability.OpenAIEndpoint.Completions,
                LLMCapability.OpenAIEndpoint.Responses,
            ),
            contextLength = 128_000,
            maxOutputTokens = 16_384,
        )

        /**
         * GPT-4.1 is a model for complex tasks.
         * It is well suited for problem solving across domains.
         *
         * 1,047,576 context window
         * 32,768 max output tokens
         * Jun 01, 2024 knowledge cutoff
         *
         * @see <a href="https://platform.openai.com/docs/models/gpt-4.1">Model page</a>
         */
        public val GPT4_1: LLModel = LLModel(
            provider = LLMProvider.OpenAI,
            id = "gpt-4.1",
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Schema.JSON.Basic,
                LLMCapability.Schema.JSON.Standard,
                LLMCapability.Speculation,
                LLMCapability.Tools,
                LLMCapability.ToolChoice,
                LLMCapability.Vision.Image,
                LLMCapability.Document,
                LLMCapability.Completion,
                LLMCapability.MultipleChoices,
                LLMCapability.OpenAIEndpoint.Completions,
                LLMCapability.OpenAIEndpoint.Responses,
            ),
            contextLength = 1_047_576,
            maxOutputTokens = 32_768,
        )

        /**
         * GPT-5 is a flagship model for coding, reasoning, and agentic tasks across domains.
         *
         * 400,000 context window
         * 128,000 max output tokens
         * Sep 30, 2024 knowledge cutoff
         * Reasoning token support
         *
         * @see <a href="https://platform.openai.com/docs/models/gpt-5">Model page</a>
         */
        public val GPT5: LLModel = LLModel(
            provider = LLMProvider.OpenAI,
            id = "gpt-5",
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Schema.JSON.Basic,
                LLMCapability.Schema.JSON.Standard,
                LLMCapability.Speculation,
                LLMCapability.Tools,
                LLMCapability.ToolChoice,
                LLMCapability.Vision.Image,
                LLMCapability.Document,
                LLMCapability.Completion,
                LLMCapability.MultipleChoices,
                LLMCapability.OpenAIEndpoint.Completions,
                LLMCapability.OpenAIEndpoint.Responses,
            ),
            contextLength = 400_000,
            maxOutputTokens = 128_000,
        )

        /**
         * GPT-5 mini is a faster, cost-efficient version of GPT-5 for well-defined tasks.
         *
         * 400,000 context window
         * 128,000 max output tokens
         * May 31, 2024 knowledge cutoff
         * Reasoning token support
         *
         * @see <a href="https://platform.openai.com/docs/models/gpt-5-mini">Model page</a>
         */
        public val GPT5Mini: LLModel = LLModel(
            provider = LLMProvider.OpenAI,
            id = "gpt-5-mini",
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Schema.JSON.Basic,
                LLMCapability.Schema.JSON.Standard,
                LLMCapability.Speculation,
                LLMCapability.Tools,
                LLMCapability.ToolChoice,
                LLMCapability.Vision.Image,
                LLMCapability.Document,
                LLMCapability.Completion,
                LLMCapability.MultipleChoices,
                LLMCapability.OpenAIEndpoint.Completions,
                LLMCapability.OpenAIEndpoint.Responses,
            ),
            contextLength = 400_000,
            maxOutputTokens = 128_000,
        )

        /**
         * GPT-5 nano is the fastest, most cost-efficient version of GPT-5.
         * Great for summarization and classification tasks.
         *
         * 400,000 context window
         * 128,000 max output tokens
         * May 31, 2024 knowledge cutoff
         * Reasoning token support
         *
         * @see <a href="https://platform.openai.com/docs/models/gpt-5-nano">Model page</a>
         */
        public val GPT5Nano: LLModel = LLModel(
            provider = LLMProvider.OpenAI,
            id = "gpt-5-nano",
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Schema.JSON.Basic,
                LLMCapability.Schema.JSON.Standard,
                LLMCapability.Speculation,
                LLMCapability.Tools,
                LLMCapability.ToolChoice,
                LLMCapability.Vision.Image,
                LLMCapability.Document,
                LLMCapability.Completion,
                LLMCapability.MultipleChoices,
                LLMCapability.OpenAIEndpoint.Completions,
                LLMCapability.OpenAIEndpoint.Responses,
            ),
            contextLength = 400_000,
            maxOutputTokens = 128_000,
        )

        /**
         * GPT-5-Codex is a version of GPT-5 optimized for agentic coding tasks in agents.
         * It's available in the **Responses API** only, and the underlying model snapshot will be regularly updated.
         *
         * 400,000 context window
         * 128,000 max output tokens
         * Reasoning token support
         *
         * @see <a href="https://platform.openai.com/docs/models/gpt-5-codex"\>Model page</a>
         */
        public val GPT5Codex: LLModel = LLModel(
            provider = LLMProvider.OpenAI,
            id = "gpt-5-codex",
            capabilities = listOf(
                LLMCapability.Completion,
                LLMCapability.Temperature,
                LLMCapability.Schema.JSON.Basic,
                LLMCapability.Schema.JSON.Standard,
                LLMCapability.Speculation,
                LLMCapability.Tools,
                LLMCapability.ToolChoice,
                LLMCapability.OpenAIEndpoint.Responses,
            ),
            contextLength = 400_000,
            maxOutputTokens = 128_000,
        )
    }

    /**
     * The `Audio` object provides access to preconfigured audio-enabled Large Language Models (LLMs).
     * These models support both audio input and output functionalities, making them suitable
     * for various audio-centric applications.
     */
    public object Audio {

        /**
         * GPT Audio is a model for audio inputs and outputs.
         *
         * 128,000 context window
         * 16,384 max output tokens
         * Oct 01, 2023 knowledge cutoff
         *
         * @see <a href="https://platform.openai.com/docs/models/gpt-audio">Model page</a>
         */
        public val GptAudio: LLModel = LLModel(
            provider = LLMProvider.OpenAI,
            id = "gpt-audio",
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Completion,
                LLMCapability.Tools,
                LLMCapability.ToolChoice,
                LLMCapability.Audio,
                LLMCapability.OpenAIEndpoint.Completions,
            ),
            contextLength = 128_000,
            maxOutputTokens = 16_384,
        )

        /**
         * GPT-4o mini Audio is a smaller,
         * more affordable version of `GPT-4o Audio` that maintains high quality while being more cost-effective.
         * It's designed to input audio or create audio outputs.
         *
         * 128,000 context window
         * 16,384 max output tokens
         * Oct 01, 2023 knowledge cutoff
         *
         * @see <a href="https://platform.openai.com/docs/models/gpt-4o-mini-audio-preview">Model page</a>
         */
        public val GPT4oMiniAudio: LLModel = LLModel(
            provider = LLMProvider.OpenAI,
            id = "gpt-4o-mini-audio-preview",
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Completion,
                LLMCapability.Tools,
                LLMCapability.ToolChoice,
                LLMCapability.Audio,
                LLMCapability.OpenAIEndpoint.Completions,
            ),
            contextLength = 128_000,
            maxOutputTokens = 16_384,
        )

        /**
         * GPT-4o Audio is a model designed to input audio or create audio outputs.
         *
         * 128,000 context window
         * 16,384 max output tokens
         * Oct 01, 2023 knowledge cutoff
         *
         * @see <a href="https://platform.openai.com/docs/models/gpt-4o-audio-preview">Model page</a>
         */
        public val GPT4oAudio: LLModel = LLModel(
            provider = LLMProvider.OpenAI,
            id = "gpt-4o-audio-preview",
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Completion,
                LLMCapability.Tools,
                LLMCapability.ToolChoice,
                LLMCapability.Audio,
                LLMCapability.OpenAIEndpoint.Completions,
            ),
            contextLength = 128_000,
            maxOutputTokens = 16_384,
        )
    }

    /**
     * The `CostOptimized` object provides a collection of cost-efficient language models
     * optimized for specific use cases. These models aim to balance quality, performance,
     * and affordability, catering to various tasks like reasoning, speculation, and tools integration.
     */
    public object CostOptimized {

        /**
         * GPT-4.1-nano is the smallest and most affordable model in the GPT-4.1 family.
         * It's designed for tasks that require basic capabilities at the lowest possible cost.
         *
         * 1,047,576 context window
         * 32,768 max output tokens
         * Jun 01, 2024 knowledge cutoff
         *
         * @see <a href="https://platform.openai.com/docs/models/gpt-4.1-nano">Model page</a>
         */
        public val GPT4_1Nano: LLModel = LLModel(
            provider = LLMProvider.OpenAI,
            id = "gpt-4.1-nano",
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Schema.JSON.Basic,
                LLMCapability.Schema.JSON.Standard,
                LLMCapability.Speculation,
                LLMCapability.Tools,
                LLMCapability.ToolChoice,
                LLMCapability.Vision.Image,
                LLMCapability.Document,
                LLMCapability.Completion,
                LLMCapability.MultipleChoices,
                LLMCapability.OpenAIEndpoint.Completions,
                LLMCapability.OpenAIEndpoint.Responses,
            ),
            contextLength = 1_047_576,
            maxOutputTokens = 32_768,
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
         * @see <a href="https://platform.openai.com/docs/models/gpt-4.1-mini">Model page</a>
         */
        public val GPT4_1Mini: LLModel = LLModel(
            provider = LLMProvider.OpenAI,
            id = "gpt-4.1-mini",
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Schema.JSON.Basic,
                LLMCapability.Schema.JSON.Standard,
                LLMCapability.Speculation,
                LLMCapability.Tools,
                LLMCapability.ToolChoice,
                LLMCapability.Vision.Image,
                LLMCapability.Document,
                LLMCapability.Completion,
                LLMCapability.MultipleChoices,
                LLMCapability.OpenAIEndpoint.Completions,
                LLMCapability.OpenAIEndpoint.Responses,
            ),
            contextLength = 1_047_576,
            maxOutputTokens = 32_768,
        )

        /**
         * GPT-4o mini is a smaller, more affordable version of GPT-4o that maintains high quality while being
         * more cost-effective. It's designed for tasks that don't require the full capabilities of GPT-4o.
         *
         * 128K context window.
         * 16,384 max output tokens
         * Oct 01, 2023 knowledge cutoff
         *
         * @see <a href="https://platform.openai.com/docs/models/gpt-4o-mini">Model page</a>
         */
        public val GPT4oMini: LLModel = LLModel(
            provider = LLMProvider.OpenAI,
            id = "gpt-4o-mini",
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Schema.JSON.Basic,
                LLMCapability.Schema.JSON.Standard,
                LLMCapability.Speculation,
                LLMCapability.Tools,
                LLMCapability.ToolChoice,
                LLMCapability.Vision.Image,
                LLMCapability.Document,
                LLMCapability.Completion,
                LLMCapability.MultipleChoices,
                LLMCapability.OpenAIEndpoint.Completions,
                LLMCapability.OpenAIEndpoint.Responses,
            ),
            contextLength = 128_000,
            maxOutputTokens = 16_384,
        )

        /**
         * See [Reasoning.O4Mini]
         */
        public val O4Mini: LLModel get() = Reasoning.O4Mini

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
         * @see <a href="https://platform.openai.com/docs/models/text-embedding-3-small">Model page</a>
         */
        public val TextEmbedding3Small: LLModel = LLModel(
            provider = LLMProvider.OpenAI,
            id = "text-embedding-3-small",
            capabilities = listOf(
                LLMCapability.Embed
            ),
            contextLength = 8_191,
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
         * @see <a href="https://platform.openai.com/docs/models/text-embedding-3-large">Model page</a>
         */
        public val TextEmbedding3Large: LLModel = LLModel(
            provider = LLMProvider.OpenAI,
            id = "text-embedding-3-large",
            capabilities = listOf(
                LLMCapability.Embed
            ),
            contextLength = 8_191,
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
         * @see <a href="https://platform.openai.com/docs/models/text-embedding-ada-002">Model page</a>
         */
        public val TextEmbeddingAda002: LLModel = LLModel(
            provider = LLMProvider.OpenAI,
            id = "text-embedding-ada-002",
            capabilities = listOf(
                LLMCapability.Embed
            ),
            contextLength = 8_191,
        )
    }
}
