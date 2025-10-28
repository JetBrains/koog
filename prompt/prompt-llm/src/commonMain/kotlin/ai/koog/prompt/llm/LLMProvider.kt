package ai.koog.prompt.llm

import kotlinx.serialization.Serializable

/**
 * Represents a sealed hierarchy for defining Large Language Model (LLM) providers.
 * Each LLM provider is uniquely identified by an id and a display name.
 *
 * This sealed class allows enumeration of specific providers like Google, OpenAI, Meta, etc.,
 * and serves as a common base type for handling providers within the system.
 *
 * @property id The unique identifier of the LLM provider.
 * @property display The human-readable name of the LLM provider.
 */
@Serializable
public abstract class LLMProvider(public val id: String, public val display: String) {
    /**
     * Represents a specialized implementation of the `LLMProvider` class corresponding to the Google provider.
     *
     * The `Google` object is a predefined instance of `LLMProvider`, with its `id` and `display` properties
     * set to "google" and "Google" respectively. It serves as an enumeration-like representation to
     * identify and work with the Google Large Language Model provider in the system.
     *
     * This object can be used in situations where the provider-specific attributes or operations
     * related to Google's language model are required.
     */
    @Serializable
    public data object Google : LLMProvider("google", "Google")

    /**
     * Represents the OpenAI provider in the Large Language Model (LLM) ecosystem.
     *
     * OpenAI, identified by the `id` value "openai", is a specific implementation
     * of the `LLMProvider` sealed class. This data object is used to define and distinguish
     * the OpenAI provider as part of the supported LLM providers.
     *
     * This provider can be utilized to configure LLM-based models, allowing developers
     * to leverage OpenAI's capabilities within various applications or systems.
     */
    @Serializable
    public data object OpenAI : LLMProvider("openai", "OpenAI")

    /**
     * Represents the Anthropic LLM provider.
     *
     * This data object is a concrete instance of the `LLMProvider` sealed class, specifically for Anthropic.
     * It defines the unique identifier and display name associated with Anthropic as a provider of large language models.
     *
     * Use this object to reference or configure language models provided by Anthropic in the context of an LLM system.
     */
    @Serializable
    public data object Anthropic : LLMProvider("anthropic", "Anthropic")

    /**
     * Represents the "Meta" large language model provider in the system.
     *
     * The `Meta` object is a concrete implementation of the `LLMProvider` class, identifying the Meta provider
     * with a predefined `id` and `display` name. It is used to associate models and capabilities specific to the
     * Meta platform across the application.
     */
    @Serializable
    public data object Meta : LLMProvider("meta", "Meta")

    /**
     * Represents Alibaba as a specific provider of Large Language Models (LLMs).
     *
     * This data object is a subclass of the `LLMProvider` sealed class, and it defines
     * Alibaba's unique identifier and display name. It is used in configurations or model
     * selections to specify Alibaba as the chosen provider.
     */
    @Serializable
    public data object Alibaba : LLMProvider("alibaba", "Alibaba")

    /**
     * Represents the OpenRouter provider within the available set of large language model providers.
     *
     * OpenRouter is identified by its unique ID ("openrouter") and display name ("OpenRouter").
     * It extends the `LLMProvider` sealed class, which serves as a base class for all supported language model providers.
     *
     * This data object adheres to the structure and serialization requirements defined by the parent class.
     * It is part of the available LLM provider hierarchy, which is used to configure and identify specific
     * providers for large language model functionalities and capabilities.
     */
    @Serializable
    public data object OpenRouter : LLMProvider("openrouter", "OpenRouter")

    /**
     * Represents the Ollama provider within the available set of large language model providers.
     *
     * Ollama is identified by its unique ID ("ollama") and display name ("Ollama").
     * It extends the `LLMProvider` sealed class, which serves as a base class for all supported language model providers.
     *
     * This data object adheres to the structure and serialization requirements defined by the parent class.
     * It is part of the available LLM provider hierarchy, which is used to configure and identify specific
     * providers for large language model functionalities and capabilities.
     */
    @Serializable
    public data object Ollama : LLMProvider("ollama", "Ollama")

    /**
     * Represents the AWS Bedrock provider within the available set of large language model providers.
     *
     * Bedrock is identified by its unique ID ("bedrock") and display name ("AWS Bedrock").
     * It extends the `LLMProvider` sealed class, which serves as a base class for all supported language model providers.
     *
     * This data object adheres to the structure and serialization requirements defined by the parent class.
     * It is part of the available LLM provider hierarchy, which is used to configure and identify specific
     * providers for large language model functionalities and capabilities.
     */
    @Serializable
    public data object Bedrock : LLMProvider("bedrock", "AWS Bedrock")

    /**
     * Represents the DeepSeek provider within the available set of large language model providers.
     *
     * DeepSeek is identified by its unique ID ("deepseek") and display name ("DeepSeek").
     * It extends the `LLMProvider` sealed class,
     * which serves as a base class for all supported language model providers.
     *
     * This data object adheres to the structure and serialization requirements defined by the parent class.
     * It is part of the available LLM provider hierarchy, which is used to configure and identify specific
     * providers for large language model functionalities and capabilities.
     */
    @Serializable
    public data object DeepSeek : LLMProvider("deepseek", "DeepSeek")

    /**
     * Represents the Mistral AI provider within the available set of large language model providers.
     *
     * MistralAI is identified by its unique ID ("mistralai") and display name ("MistralAI").
     * It extends the `LLMProvider` sealed class,
     * which serves as a base class for all supported language model providers.
     *
     * This data object adheres to the structure and serialization requirements defined by the parent class.
     * It is part of the available LLM provider hierarchy, which is used to configure and identify specific
     * providers for large language model functionalities and capabilities.
     */
    @Serializable
    public data object MistralAI : LLMProvider("mistralai", "MistralAI")
}
