package ai.koog.prompt.executor.clients.openrouter

import ai.koog.prompt.executor.clients.LLModelDefinitions
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

/**
 * OpenRouter models
 * Models available through the OpenRouter API
 */
public object OpenRouterModels: LLModelDefinitions {
    private val standardCapabilities: List<LLMCapability> = listOf(
        LLMCapability.Temperature,
        LLMCapability.Schema.JSON.Full,
        LLMCapability.Speculation,
        LLMCapability.Tools,
        LLMCapability.ToolChoice,
        LLMCapability.Completion
    )

    /**
     * Free model for testing and development
     */
    public val Phi4Reasoning: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "microsoft/phi-4-reasoning:free",
        capabilities = standardCapabilities
    )

    // Multimodal capabilities (including vision)
    private val multimodalCapabilities: List<LLMCapability> = standardCapabilities + LLMCapability.Vision.Image
    
    /**
     * Represents the Claude 3 Opus model provided by Anthropic through OpenRouter.
     *
     * This model instance is configured with the following attributes:
     * - Provider: OpenRouter, which acts as the intermediary platform for model access and interactions.
     * - Identifier: "anthropic/claude-3-opus", uniquely specifying the Claude 3 Opus variant offered by Anthropic.
     * - Capabilities: A predefined set of multimodal capabilities that dictate the functionalities supported by this LLM instance.
     *
     * Claude 3 Opus is designed to support various advanced language model tasks enabled by its multimodal features,
     * and is suitable for integration through systems compatible with the OpenRouter provider.
     */
// Anthropic models
    public val Claude3Opus: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "anthropic/claude-3-opus",
        capabilities = multimodalCapabilities
    )

    /**
     * Represents a predefined language model configuration for the "Claude 3 Sonnet" model.
     *
     * This variable defines an instance of the `LLModel` class using the `OpenRouter` provider.
     * The model is identified with the ID "anthropic/claude-3-sonnet" and supports multimodal capabilities.
     *
     * - `provider`: Specifies the provider of the model, in this case, `LLMProvider.OpenRouter`.
     * - `id`: The unique identifier for the model, referring to "anthropic/claude-3-sonnet".
     * - `capabilities`: A list of capabilities supported by the model, defined as multimodal capabilities.
     */
    public val Claude3Sonnet: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "anthropic/claude-3-sonnet",
        capabilities = multimodalCapabilities
    )

    /**
     * Represents the Claude v3 Haiku model provided through the OpenRouter platform.
     *
     * This model is designed to handle multimodal capabilities and is identified by the
     * ID "anthropic/claude-3-haiku". It uses the OpenRouter provider as its delivery system.
     *
     * @property provider Specifies the provider of the model; in this case, OpenRouter.
     * @property id The unique identifier for the Claude v3 Haiku model.
     * @property capabilities A list detailing the multimodal capabilities supported by the model.
     */
    public val Claude3Haiku: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "anthropic/claude-3-haiku",
        capabilities = multimodalCapabilities
    )

    /**
     * Represents the GPT-4 model hosted on OpenRouter.
     *
     * This variable defines an instance of the `LLModel` type,
     * specifying the OpenRouter provider and using the identifier `"openai/gpt-4"`.
     * It leverages a standard set of capabilities for interaction.
     */
    public val GPT4: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "openai/gpt-4",
        capabilities = standardCapabilities
    )

    /**
     * GPT4o represents an instance of the GPT-4 model obtained via the OpenRouter provider.
     * It is pre-configured with the specified identifier and capabilities.
     *
     * @property provider The language model provider, specifically OpenRouter for this instance.
     * @property id The unique identifier for the GPT-4o model.
     * @property capabilities Defines the multimodal capabilities of this model.
     */
    public val GPT4o: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "openai/gpt-4o",
        capabilities = multimodalCapabilities
    )

    /**
     * Represents an instance of the GPT-4 Turbo model hosted via OpenRouter.
     *
     * This model utilizes the OpenRouter provider and is identified with the unique ID
     * `openai/gpt-4-turbo`. It supports multimodal capabilities, making it suitable for
     * a range of advanced generative tasks such as text processing and creation.
     *
     * @property provider The provider hosting the model. In this case, it is `OpenRouter`.
     * @property id The unique identifier of the model, which is `openai/gpt-4-turbo`.
     * @property capabilities A list specifying the supported capabilities of this model,
     * such as multimodal functionality.
     */
    public val GPT4Turbo: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "openai/gpt-4-turbo",
        capabilities = multimodalCapabilities
    )

    /**
     * Represents the GPT-3.5-Turbo language model provided by the OpenRouter platform.
     *
     * This variable defines an instance of the `LLModel` class with the following properties:
     * - `provider`: Specifies that the model is hosted on the OpenRouter platform.
     * - `id`: The unique identifier for the model, "openai/gpt-3.5-turbo".
     * - `capabilities`: A predefined set of standard capabilities supported by this model.
     *
     * GPT-3.5-Turbo is a powerful, general-purpose large language model capable of tasks
     * such as natural language understanding, text generation, summarization, and more.
     */
    public val GPT35Turbo: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "openai/gpt-3.5-turbo",
        capabilities = standardCapabilities
    )

    /**
     * Represents the Google Gemini 1.5 Pro language model accessed through the OpenRouter provider.
     *
     * This model supports multimodal capabilities, which enable handling multiple types of input or tasks.
     * It is identified by the `google/gemini-1.5-pro` model ID within the OpenRouter ecosystem.
     */
    public val Gemini14Pro: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "google/gemini-1.5-pro",
        capabilities = multimodalCapabilities
    )

    /**
     * Represents the Gemini 1.5 Flash language model provided via the OpenRouter platform.
     *
     * This language model is identified by its unique `id` and supports multimodal capabilities,
     * enabling it to handle various modes of input and output effectively.
     */
    public val Gemini15Flash: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "google/gemini-1.5-flash",
        capabilities = multimodalCapabilities
    )

    /**
     * Represents the Llama3 model configuration provided by OpenRouter.
     * This model is identified by the unique ID "meta/llama-3-70b" and
     * supports the standard set of language model capabilities.
     *
     * @property provider The provider of the language model, here specified as OpenRouter.
     * @property id The unique identifier for the Llama3 model.
     * @property capabilities A list of capabilities that define the model's features and functionalities.
     */
    public val Llama3: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "meta/llama-3-70b",
        capabilities = standardCapabilities
    )

    /**
     * Represents the Llama 3 model with 70 billion parameters designed for instruction tuning.
     * This model is provided via the OpenRouter provider and is configured with standard capabilities.
     *
     * @property provider The provider of the LLM, which is OpenRouter in this case.
     * @property id The unique identifier for the model.
     * @property capabilities The list of capabilities supported by this model.
     */
    public val Llama3Instruct: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "meta/llama-3-70b-instruct",
        capabilities = standardCapabilities
    )

    /**
     * Represents the Mistral 7B language model.
     *
     * Mistral 7B is a 7-billion parameter model provided by the OpenRouter service. It leverages
     * standard capabilities for language model functionality, such as text generation and completion.
     *
     * @property provider The service provider for the model, which in this case is OpenRouter.
     * @property id The unique identifier of the model within the provider's system.
     * @property capabilities A list of standard capabilities supported by the model.
     */
    public val Mistral7B: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "mistral/mistral-7b",
        capabilities = standardCapabilities
    )

    /**
     * Represents the Mixtral 8x7B language model configuration.
     *
     * This variable defines an instance of the LLModel class, designed for use with the OpenRouter
     * provider. The model's identifier is "mistral/mixtral-8x7b" and it is equipped with standard
     * capabilities, making it suitable for a variety of general-purpose large language model tasks.
     *
     * Key properties:
     * - Provider: OpenRouter, a framework for routing various AI model requests.
     * - ID: "mistral/mixtral-8x7b", indicating the specific model version.
     * - Capabilities: Includes standard abilities defined in the system configuration.
     */
    public val Mixtral8x7B: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "mistral/mixtral-8x7b",
        capabilities = standardCapabilities
    )

    /**
     * Represents the Claude 3 Vision Sonnet model provided by Anthropic, accessible through OpenRouter.
     *
     * This model supports multimodal capabilities, enabling it to process and generate outputs
     * across different modalities such as text and vision. It is identified by the unique ID
     * "anthropic/claude-3-sonnet-vision".
     *
     * @property provider The provider through which the model is accessed, in this case, OpenRouter.
     * @property id The unique identifier for the Claude 3 Vision Sonnet model.
     * @property capabilities A list of supported capabilities indicating the model's features.
     */
// Anthropic Vision models
    public val Claude3VisionSonnet: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "anthropic/claude-3-sonnet-vision",
        capabilities = multimodalCapabilities
    )

    /**
     * Represents the Claude 3 Vision model provided via OpenRouter.
     *
     * This model is a multimodal large language model developed by Anthropic,
     * accessible through the OpenRouter provider. Its identifier is
     * `"anthropic/claude-3-opus-vision"`, and it supports multimodal capabilities.
     */
    public val Claude3VisionOpus: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "anthropic/claude-3-opus-vision",
        capabilities = multimodalCapabilities
    )

    /**
     * Represents the Claude 3 Vision model provided via OpenRouter.
     *
     * This model is a multimodal AI model enabling advanced capabilities, such as processing both
     * textual and visual inputs. It utilizes the OpenRouter infrastructure to facilitate access
     * to Anthropic's Claude 3 model with vision support.
     *
     * @property provider The provider of the model, which in this case is OpenRouter.
     * @property id The unique identifier for the model, specifying its version and modality.
     * @property capabilities A list of capabilities that define the functionality provided by the model.
     */
    public val Claude3VisionHaiku: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "anthropic/claude-3-haiku-vision",
        capabilities = multimodalCapabilities
    )
}
