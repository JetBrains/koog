package ai.koog.spring

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for the Koog library used for integrating with OpenAI LLM provider.
 * These properties are used in conjunction with the [KoogAutoConfiguration] auto-configuration class to initialize and
 * configure respective client implementations.
 *
 * Configuration prefix: `ai.koog.openai`
 *
 * @param apiKey The API key used to authenticate requests to the provider's service
 * @param baseUrl The base URL of the provider's API endpoint. By default, it is set to `https://api.openai.com`
 */
@ConfigurationProperties(prefix = OpenAIKoogProperties.PREFIX)
public class OpenAIKoogProperties(
    public val apiKey: String = "",
    public val baseUrl: String = "https://api.openai.com",
    public val retry: RetryConfigKoogProperties? = null
) {
    /**
     * Companion object for the OpenAIKoogProperties class, providing constant values and
     * utilities associated with the configuration of OpenAI-related properties.
     */
    public companion object {
        /**
         * Prefix constant used for configuration OpenAI-related properties in the Koog framework.
         */
        public const val PREFIX: String = "ai.koog.openai"
    }
}
