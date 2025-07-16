package ai.koog.prompt.llm

import kotlinx.serialization.Serializable

/**
 * Represents a specific capability or feature of an LLM (Large Language Model). This is a sealed class,
 * where each capability is represented as a subclass or data object.
 *
 * @property id The unique identifier for this capability.
 */
@Serializable
public sealed class LLMCapability(public val id: String) {
    /**
     * Represents the capability of the language model to perform speculative responses.
     * This capability allows the model to generate responses with varying degrees of likelihood,
     * often used for exploratory or hypothetical scenarios.
     *
     * Speculation may be beneficial in situations where creative or less deterministic answers
     * are preferred, providing a broader range of potential outcomes.
     */
    @Serializable
    public data object Speculation : LLMCapability("speculation")

    /**
     * Represents the `temperature` capability of a language model.
     *
     * This capability is utilized to adjust the model's response randomness or creativity levels.
     * Higher temperature values typically produce more diverse outputs, while lower values lead to more focused and deterministic responses.
     *
     * Belongs to the `LLMCapability` sealed class hierarchy, which defines various features and behaviors supported by language models.
     */
    @Serializable
    public data object Temperature : LLMCapability("temperature")

    /**
     * Represents the capability of tools within the LLM capability hierarchy.
     *
     * The Tools capability is typically used to indicate support for external tool usage
     * or interaction by a language model. This can include functionalities such as
     * executing specific tools or integrating with external systems. It is a predefined
     * constant within the set of capabilities available for an LLModel.
     *
     * Use this capability to specify or check tool interaction abilities in a model's configuration.
     */
    @Serializable
    public data object Tools : LLMCapability("tools")

    /**
     * Represents how tools calling can be configured for the LLM.
     *
     * Depending on the LLM, will configure it to generate:
     * - Automatically choose to generate either text or tool call
     * - Generate only tool calls, never text
     * - Generate only text, never tool calls
     * - Force to call one specific tool among the defined tools
     */
    @Serializable
    public data object ToolChoice : LLMCapability("toolChoice")

    /**
     * Represents an LLM capability to generate multiple independent reply choices to a single prompt.
     */
    @Serializable
    public data object MultipleChoices : LLMCapability("multipleChoices")

    /**
     * Represents a large language model (LLM) capability associated with vision-based tasks.
     * This capability is typically used in models that can process, analyze, and infer insights
     * from visual data or visual representations.
     */
    @Serializable
    public sealed class Vision(public val visionType: String) : LLMCapability(visionType) {

        /**
         * Represents a specific capability for handling image-related vision tasks within a large language model (LLM).
         *
         * This class is a concrete implementation of the `Vision` sealed class, focusing on tasks such as image analysis,
         * recognition, and interpretation. It is designed to enable models with the ability to process and infer
         * insights from visual data represented as static images.
         *
         * The `Image` capability is typically used in scenarios where the model's functionality includes
         * understanding image content, performing image-to-text generation, or tasks that require visual comprehension.
         */
        @Serializable
        public data object Image : Vision("image")

        /**
         * Represents the video processing capability within vision-based tasks.
         *
         * This capability is used to handle video-related functionalities, including analyzing
         * and processing video data. It is part of the sealed hierarchy for vision-based
         * capabilities and provides a concrete implementation specific to video inputs.
         */
        @Serializable
        public data object Video : Vision("video")
    }

    /**
     * Represents a specialized capability for audio-related functionalities in the context of a LLM.
     * This capability is used in models that can involving audio processing,
     * such as transcription, audio generation, or audio-based interactions.
     */
    @Serializable
    public data object Audio: LLMCapability("audio")

    /**
     * Represents a specific language model capability associated with handling documents.
     */
    @Serializable
    public data object Document: LLMCapability("document")

    /**
     * Represents the capability of generating embeddings within the context of language models.
     *
     * The `Embed` capability allows models to process input text and generate vector embeddings,
     * which are numerical representations of text that enable similarity comparisons,
     * clustering, and other forms of vector-based analysis.
     *
     * This capability can be utilized in tasks like semantic search, document clustering,
     * or other operations requiring an understanding of textual similarity.
     */
    @Serializable
    public data object Embed : LLMCapability("embed")

    /**
     * Represents the "completion" capability for Language Learning Models (LLMs). This capability
     * typically encompasses the generation of text or content based on the given input context.
     * It belongs to the `LLMCapability` sealed class hierarchy and is identifiable by the `embed` ID.
     *
     * This capability can be utilized within an LLM to perform tasks such as completing a sentence,
     * generating suggestions, or producing content that aligns with the given input data and context.
     */
    @Serializable
    public data object Completion : LLMCapability("completion")

    /**
     * Represents a capability in the Large Language Model (LLM) for caching.
     *
     * Use this capability to represent models that support caching functionalities.
     */
    @Serializable
    public data object PromptCaching: LLMCapability("promptCaching")

    /**
     * Represents a capability in the Large Language Model (LLM) for content moderation.
     *
     * This capability allows the model to analyze text for potentially harmful content
     * and classify it according to various categories such as harassment, hate speech,
     * self-harm, sexual content, violence, etc.
     */
    @Serializable
    public data object Moderation: LLMCapability("moderation")

    /**
     * Represents a structured schema capability for a language model. The schema defines certain characteristics or
     * functionalities related to data interaction and encoding using specific formats.
     *
     * This class is designed to encapsulate different schema configurations that the language model can support,
     * such as JSON processing.
     *
     * @property lang The language format associated with the schema.
     */
    @Serializable
    public sealed class Schema(public val lang: String) : LLMCapability("$lang-schema") {
        /**
         * Represents a sealed class defining JSON schema support as a part of an AI model's capability.
         * Each subtype of this class specifies a distinct level of JSON support.
         *
         * @property support Describes the type of JSON support (e.g., "simple", "full").
         */
        @Serializable
        public sealed class JSON(public val support: String) : Schema("json-$support") {
            /**
             * Represents a simple JSON schema support capability within the context of language learning models (LLMs).
             * Used to specify lightweight or basic JSON processing capabilities.
             */
            @Serializable
            public data object Simple : JSON("simple")

            /**
             * Represents a data object for the "full" JSON schema type.
             *
             * This class provides a specific implementation of the parent sealed class `JSON`, with
             * the `support` parameter set to `"full"`. It is used to define JSON schema support for full capabilities.
             */
            @Serializable
            public data object Full : JSON("full")
        }
    }
}
