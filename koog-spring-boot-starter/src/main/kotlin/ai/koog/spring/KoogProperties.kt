package ai.koog.spring

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty

/**
 * Configuration properties for the Koog library used for integrating with various large language model (LLM) providers.
 *
 * This class centralizes the configuration settings for multiple LLM providers, such as Anthropic,
 * Google, Ollama, OpenAI, and OpenRouter, making them accessible via a unified configuration prefix.
 * The properties for each provider are nested and configurable independently using specific keys.
 *
 * The default base URLs and settings for the providers are preconfigured but can be overridden
 * through application properties or environment variables.
 *
 * Configuration prefix: "ai.koog"
 */
@ConfigurationProperties(prefix = KoogProperties.PREFIX)
public class KoogProperties {
    /**
     * Companion object for the KoogProperties class, providing constant values and
     * utilities associated with the configuration of Koog-related properties.
     */
    public companion object {
        /**
         * Prefix constant used for configuration properties in the Koog framework.
         *
         * This value defines the root prefix under which all Koog-related configuration properties
         * should be declared in the application's configuration files. It serves as a namespace
         * to avoid conflicts with other configuration keys.
         *
         * Example usage within application properties:
         * - "ai.koog.google.api-key" for Google client configuration
         * - "ai.koog.openai.api-key" for OpenAI client configuration
         */
        public const val PREFIX: String = "ai.koog"
    }

    /**
     * Holds configuration properties specific to the Anthropic API client.
     * This variable allows users to configure the API key and base URL
     * for interacting with the Anthropic service.
     *
     * It is represented as a nested configuration property and uses
     * the [ProviderKoogProperties] class to encapsulate the settings.
     *
     * Default value for [ProviderKoogProperties.baseUrl] is set to `https://api.anthropic.com`.
     */
    @NestedConfigurationProperty
    public var anthropicClientProperties: ProviderKoogProperties =
        ProviderKoogProperties(baseUrl = "https://api.anthropic.com")

    /**
     * Configuration properties representing settings required for integration with Google's Generative Language API.
     *
     * This nested property is part of the [KoogProperties] configuration class. It provides
     * customization options such as the [ProviderKoogProperties.apiKey] and [ProviderKoogProperties.baseUrl] that are necessary
     * for interacting with Google's machine learning services.
     *
     * The [ProviderKoogProperties.baseUrl] is pre-configured to the default API endpoint for Google's Generative Language service.
     */
    @NestedConfigurationProperty
    public var googleClientProperties: ProviderKoogProperties =
        ProviderKoogProperties(baseUrl = "https://generativelanguage.googleapis.com")

    /**
     * Configuration properties for the Ollama LLM (Large Language Model) client.
     * This property is used to define and manage settings specific to the Ollama client,
     * including the base URL for API interactions.
     *
     * The default base URL is set to `http://localhost:11434`.
     *
     * This property is nested under the main configuration class [KoogProperties]
     * and can be customized via application properties files using the `ai.koog.ollama` prefix.
     */
    @NestedConfigurationProperty
    public var ollamaClientProperties: OllamaKoogProperties = OllamaKoogProperties()

    /**
     * Configuration properties for the OpenAI client.
     *
     * This property is part of the [KoogProperties] configuration class and is used to define
     * settings for connecting to the OpenAI API. It supports the specification of an API key
     * and a base URL for the OpenAI client.
     *
     * By default, the [ProviderKoogProperties.baseUrl] is set to `https://api.openai.com`. The [ProviderKoogProperties.apiKey] must be provided
     * in the application's configuration for the OpenAI integration to be active.
     *
     * This property is marked as a [NestedConfigurationProperty], indicating that it is a
     * nested configuration element within [KoogProperties].
     */
    @NestedConfigurationProperty
    public var openAIClientProperties: ProviderKoogProperties =
        ProviderKoogProperties(baseUrl = "https://api.openai.com")

    /**
     * Represents the configuration properties for the OpenRouter LLM client integration.
     *
     * Contains settings such as the base URL and API key for accessing the OpenRouter
     * platform. This property is nested within the main application configuration and
     * used to initialize the corresponding OpenRouter client.
     */
    @NestedConfigurationProperty
    public var openRouterClientProperties: ProviderKoogProperties =
        ProviderKoogProperties(baseUrl = "https://openrouter.ai")
}

/**
 * Represents the configuration properties required for a provider in Koog's multi-LLM settings.
 *
 * This class is used to define client-specific settings such as the API key and base URL for
 * various LLM (Large Language Model) providers, including Anthropic, Google, OpenAI, and OpenRouter.
 * These properties are used in conjunction with the auto-configuration classes to initialize and
 * configure respective client implementations.
 *
 * @param apiKey The API key used to authenticate requests to the provider's service.
 * @param baseUrl The base URL of the provider's API endpoint.
 */
public data class ProviderKoogProperties(
    val apiKey: String = "",
    val baseUrl: String
)

/**
 * Represents the configuration properties for the Ollama client.
 *
 * This class contains settings required to configure and interact with the Ollama client,
 * such as the base URL for the API.
 *
 * @property baseUrl The base URL of the Ollama API. Defaults to "http://localhost:11434".
 */
public data class OllamaKoogProperties(
    val baseUrl: String = "http://localhost:11434"
)
