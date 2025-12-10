package ai.koog.prompt.executor.clients.openrouter

import ai.koog.prompt.executor.clients.LLModelDefinitions
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

/**
 * OpenRouter models
 * Models available through the OpenRouter API
 */
public object OpenRouterModels : LLModelDefinitions {
    /**
     * Standard capabilities available for most OpenRouter models.
     * Includes temperature control, JSON schema support, speculation, tools, and completion.
     */
    private val standardCapabilities: List<LLMCapability> = listOf(
        LLMCapability.Temperature,
        LLMCapability.Speculation,
        LLMCapability.Tools,
        LLMCapability.Completion,
    )

    /**
     * Structured output support and tool choice.
     */
    private val additionalCapabilities: List<LLMCapability> = listOf(
        LLMCapability.Schema.JSON.Standard,
        LLMCapability.ToolChoice
    )

    /**
     * Multimodal capabilities including vision support.
     * Extends standard capabilities with image vision processing.
     */
    private val multimodalCapabilities: List<LLMCapability> = standardCapabilities + LLMCapability.Vision.Image

    /**
     * Free model for testing and development.
     *
     * @see <a href="https://huggingface.co/microsoft/Phi-4-reasoning">
     */
    public val Phi4Reasoning: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "microsoft/phi-4-reasoning:free",
        capabilities = standardCapabilities,
        contextLength = 32_768,
    )

    /**
     * Represents the Claude 3 Opus model provided by Anthropic through OpenRouter.
     *
     * Claude 3 Opus is designed to support various advanced language model tasks enabled by its multimodal features,
     * and is suitable for integration through systems compatible with the OpenRouter provider.
     */
    public val Claude3Opus: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "anthropic/claude-3-opus",
        capabilities = multimodalCapabilities,
        contextLength = 200_000,
        maxOutputTokens = 4_096,
    )

    /**
     * Represents a predefined language model configuration for the "Claude 3 Sonnet" model.
     *
     * This variable defines an instance of the `LLModel` class using the `OpenRouter` provider.
     * The model is identified with the ID "anthropic/claude-3-sonnet" and supports multimodal capabilities.
     */
    public val Claude3Sonnet: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "anthropic/claude-3-sonnet",
        capabilities = multimodalCapabilities,
        contextLength = 200_000,
        maxOutputTokens = 4_096,
    )

    /**
     * Represents the Claude v3 Haiku model provided through the OpenRouter platform.
     *
     * This model is designed to handle multimodal capabilities and is identified by the
     * ID "anthropic/claude-3-haiku". It uses the OpenRouter provider as its delivery system.
     */
    public val Claude3Haiku: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "anthropic/claude-3-haiku",
        capabilities = multimodalCapabilities,
        contextLength = 200_000,
        maxOutputTokens = 4_096,
    )

    /**
     * Claude 3.5 Sonnet model with enhanced capabilities and larger output token limit.
     * Supports multimodal inputs including text and vision.
     */
    public val Claude3_5Sonnet: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "anthropic/claude-3.5-sonnet",
        capabilities = multimodalCapabilities,
        contextLength = 200_000,
        maxOutputTokens = 8_200,
    )

    /**
     * Claude 3.7 Sonnet model with significantly expanded output capacity.
     * Features advanced multimodal capabilities and large context window.
     */
    public val Claude3_7Sonnet: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "anthropic/claude-3.7-sonnet",
        capabilities = multimodalCapabilities,
        contextLength = 200_000,
        maxOutputTokens = 64_000,
    )

    /**
     * Claude 4 Sonnet model representing the latest generation of Anthropic's models.
     * Offers enhanced performance with multimodal support and extensive output capacity.
     */
    public val Claude4Sonnet: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "anthropic/claude-sonnet-4",
        capabilities = multimodalCapabilities,
        contextLength = 200_000,
        maxOutputTokens = 64_000,
    )

    /**
     * Claude 4.1 Opus model, the premium tier offering advanced reasoning capabilities.
     * Designed for complex tasks requiring sophisticated multimodal understanding.
     */
    public val Claude4_1Opus: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "anthropic/claude-opus-4.1",
        capabilities = multimodalCapabilities,
        contextLength = 200_000,
        maxOutputTokens = 32_000,
    )

    /**
     * Claude Haiku 4.5 is Anthropic’s fastest and most efficient model,
     * delivering near-frontier intelligence at a fraction of the cost and latency of larger Claude models.
     */
    public val Claude4_5Haiku: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "anthropic/claude-haiku-4.5",
        capabilities = multimodalCapabilities + additionalCapabilities,
        contextLength = 200_000,
        maxOutputTokens = 64_000,
    )

    /**
     * Claude Sonnet 4.5 is Anthropic’s most advanced Sonnet model to date, optimized for real-world agents
     * and coding workflows.
     */
    public val Claude4_5Sonnet: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "anthropic/claude-sonnet-4.5",
        capabilities = multimodalCapabilities + additionalCapabilities,
        contextLength = 1_000_000,
        maxOutputTokens = 64_000,
    )

    /**
     * Claude Opus 4.5 is Anthropic’s frontier reasoning model optimized for complex software engineering,
     * agentic workflows, and long-horizon computer use.
     */
    public val Claude4_5Opus: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "anthropic/claude-opus-4.5",
        capabilities = multimodalCapabilities + additionalCapabilities,
        contextLength = 200_000,
        maxOutputTokens = 32_000,
    )

    /**
     * Represents the GPT-4o-mini model hosted on OpenRouter.
     *
     * It leverages a standard set of capabilities for interaction.
     */
    public val GPT4oMini: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "openai/gpt-4o-mini",
        capabilities = multimodalCapabilities + additionalCapabilities,
        contextLength = 128_000,
        maxOutputTokens = 16_400,
    )

    /**
     * Represents the GPT-5 Chat model hosted on OpenRouter.
     *
     * It leverages a set of multimodal capabilities for interaction.
     */
    public val GPT5Chat: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "openai/gpt-5-chat",
        capabilities = multimodalCapabilities + additionalCapabilities,
        contextLength = 400_000,
        maxOutputTokens = 128_000,
    )

    /**
     * Represents the GPT-5 model hosted on OpenRouter.
     *
     * This variable defines an instance of the `LLModel` type,
     * specifying the OpenRouter provider and using the identifier `"openai/gpt-5"`.
     * It leverages a standard set of capabilities for interaction.
     */
    public val GPT5: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "openai/gpt-5",
        capabilities = standardCapabilities + additionalCapabilities,
        contextLength = 400_000,
    )

    /**
     * Represents the GPT-5 Mini model hosted on OpenRouter.
     *
     * This variable defines an instance of the `LLModel` type,
     * specifying the OpenRouter provider and using the identifier `"openai/gpt-5-mini"`.
     * It leverages a standard set of capabilities for interaction.
     */
    public val GPT5Mini: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "openai/gpt-5-mini",
        capabilities = standardCapabilities + additionalCapabilities,
        contextLength = 400_000,
    )

    /**
     * Represents the GPT-5 Nano model hosted on OpenRouter.
     *
     * This variable defines an instance of the `LLModel` type,
     * specifying the OpenRouter provider and using the identifier `"openai/gpt-5-nano"`.
     * It leverages a standard set of capabilities for interaction.
     */
    public val GPT5Nano: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "openai/gpt-5-nano",
        capabilities = standardCapabilities + additionalCapabilities,
        contextLength = 400_000,
    )

    /**
     * Represents the gpt-oss-120b model hosted on OpenRouter.
     *
     * This variable defines an instance of the `LLModel` type,
     * specifying the OpenRouter provider and using the identifier `"openai/gpt-oss-120b"`.
     * It leverages a standard set of capabilities for interaction.
     */
    public val GPT_OSS_120b: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "openai/gpt-oss-120b",
        capabilities = standardCapabilities + additionalCapabilities,
        contextLength = 400_000,
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
        capabilities = standardCapabilities + additionalCapabilities,
        contextLength = 32_768,
    )

    /**
     * GPT4o represents an instance of the GPT-4 model obtained via the OpenRouter provider.
     * It is pre-configured with the specified identifier and capabilities.
     */
    public val GPT4o: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "openai/gpt-4o",
        capabilities = multimodalCapabilities + additionalCapabilities,
        contextLength = 128_000,
    )

    /**
     * Represents an instance of the GPT-4 Turbo model hosted via OpenRouter.
     *
     * This model utilizes the OpenRouter provider and is identified with the unique ID
     * `openai/gpt-4-turbo`. It supports multimodal capabilities, making it suitable for
     * a range of advanced generative tasks such as text processing and creation.
     */
    public val GPT4Turbo: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "openai/gpt-4-turbo",
        capabilities = multimodalCapabilities + additionalCapabilities,
        contextLength = 128_000,
    )

    /**
     * Represents the GPT-3.5-Turbo language model provided by the OpenRouter platform.
     *
     * GPT-3.5-Turbo is a powerful, general-purpose large language model capable of tasks
     * such as natural language understanding, text generation, summarization, and more.
     */
    public val GPT35Turbo: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "openai/gpt-3.5-turbo",
        capabilities = standardCapabilities + additionalCapabilities,
        contextLength = 16_385,
    )

    /**
     * Represents the Llama3 model configuration provided by OpenRouter.
     * This model is identified by the unique ID "meta/llama-3-70b" and
     * supports the standard set of language model capabilities.
     *
     * @see <a href="https://huggingface.co/meta-llama/Meta-Llama-3-70B">
     */
    public val Llama3: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "meta/llama-3-70b",
        capabilities = standardCapabilities + additionalCapabilities,
        contextLength = 8_000,
    )

    /**
     * Represents the Llama 3 model with 70 billion parameters designed for instruction tuning.
     * This model is provided via the OpenRouter provider and is configured with standard capabilities.
     *
     * @see <a href="https://huggingface.co/meta-llama/Meta-Llama-3-70B-Instruct">
     */
    public val Llama3Instruct: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "meta/llama-3-70b-instruct",
        capabilities = standardCapabilities + additionalCapabilities,
        contextLength = 8_000,
    )

    /**
     * Represents the Mistral 7B language model.
     *
     * Mistral 7B is a 7-billion parameter model provided by the OpenRouter service. It leverages
     * standard capabilities for language model functionality, such as text generation and completion.
     *
     * @see <a href="https://huggingface.co/mistralai/Mistral-7B-Instruct-v0.3">
     */
    public val Mistral7B: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "mistralai/mistral-7b-instruct",
        capabilities = standardCapabilities,
        contextLength = 32_768,
    )

    /**
     * Represents the Mixtral 8x7B language model configuration.
     *
     * This variable defines an instance of the LLModel class, designed for use with the OpenRouter
     * provider. The model's identifier is "mistral/mixtral-8x7b" and it is equipped with standard
     * capabilities, making it suitable for a variety of general-purpose large language model tasks.
     *
     * @see <a href="https://huggingface.co/mistralai/Mixtral-8x7B-Instruct-v0.1">
     */
    public val Mixtral8x7B: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "mistralai/mixtral-8x7b-instruct",
        capabilities = standardCapabilities,
        contextLength = 32_768,
    )

    /**
     * Represents the Claude 3 Vision Sonnet model provided by Anthropic, accessible through OpenRouter.
     *
     * This model supports multimodal capabilities, enabling it to process and generate outputs
     * across different modalities such as text and vision. It is identified by the unique ID
     * "anthropic/claude-3-sonnet-vision".
     */
    public val Claude3VisionSonnet: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "anthropic/claude-3-sonnet-vision",
        capabilities = multimodalCapabilities,
        contextLength = 200_000,
        maxOutputTokens = 4_096,
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
        capabilities = multimodalCapabilities,
        contextLength = 200_000,
        maxOutputTokens = 4_096,
    )

    /**
     * Represents the Claude 3 Vision model provided via OpenRouter.
     *
     * This model is a multimodal AI model enabling advanced capabilities, such as processing both
     * textual and visual inputs. It utilizes the OpenRouter infrastructure to facilitate access
     * to Anthropic's Claude 3 model with vision support.
     */
    public val Claude3VisionHaiku: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "anthropic/claude-3-haiku-vision",
        capabilities = multimodalCapabilities,
        contextLength = 200_000,
        maxOutputTokens = 4_096,
    )

    /**
     * DeepSeek V3 model from March 2024 release.
     * Offers extensive context length with matching output token capacity.
     */
    public val DeepSeekV30324: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "deepseek/deepseek-chat-v3-0324",
        capabilities = standardCapabilities,
        contextLength = 163_800,
        maxOutputTokens = 163_800,
    )

    /**
     * Gemini 2.5 Flash Lite model optimized for speed and efficiency.
     * Features multimodal capabilities with very large context window.
     */
    public val Gemini2_5FlashLite: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "google/gemini-2.5-flash-lite",
        capabilities = multimodalCapabilities + additionalCapabilities,
        contextLength = 1_048_576,
        maxOutputTokens = 65_600,
    )

    /**
     * Gemini 2.5 Flash model balancing performance and speed.
     * Supports multimodal inputs with extensive context and output capacity.
     */
    public val Gemini2_5Flash: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "google/gemini-2.5-flash",
        capabilities = multimodalCapabilities + additionalCapabilities,
        contextLength = 1_048_576,
        maxOutputTokens = 65_600,
    )

    /**
     * Gemini 2.5 Pro model representing Google's premium offering.
     * Provides advanced multimodal capabilities with maximum context and output limits.
     */
    public val Gemini2_5Pro: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "google/gemini-2.5-pro",
        capabilities = multimodalCapabilities + additionalCapabilities,
        contextLength = 1_048_576,
        maxOutputTokens = 65_600,
    )

    /**
     * Qwen 2.5 model with 72B parameters from Alibaba.
     * Supports advanced language understanding and generation capabilities.
     */
    public val Qwen2_5: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "qwen/qwen-2.5-72b-instruct",
        capabilities = standardCapabilities + additionalCapabilities,
        contextLength = 131_072,
        maxOutputTokens = 8_192,
    )

    /**
     * Qwen 3 model with 8B parameters from Alibaba.
     * Multimodal vision-language model from the Qwen3-VL series, built for high-fidelity understanding
     * and reasoning across text, images, and video.
     */
    public val Qwen3VL: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "qwen/qwen3-vl-8b-instruct",
        capabilities = multimodalCapabilities + additionalCapabilities,
        contextLength = 131_072,
        maxOutputTokens = 33_000,
    )
}
