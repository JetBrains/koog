package ai.koog.spring

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for the Koog library used for integrating with OpenRouter LLM provider.
 * These properties are used in conjunction with the [KoogAutoConfiguration] auto-configuration class to initialize and
 * configure respective client implementations.
 *
 * Configuration prefix: `ai.koog.openrouter`
 *
 * @param apiKey The API key used to authenticate requests to the provider's service
 * @param baseUrl The base URL of the provider's API endpoint. By default, it is set to `https://openrouter.ai`
 */
@ConfigurationProperties(prefix = OpenRouterKoogProperties.PREFIX)
public class OpenRouterKoogProperties(
    public val apiKey: String = "",
    public val baseUrl: String = "https://openrouter.ai"
) {
    /**
     * Companion object for the OpenRouterKoogProperties class, providing constant values and
     * utilities associated with the configuration of OpenRouter-related properties.
     */
    public companion object Companion {
        /**
         * Prefix constant used for configuration OpenRouter-related properties in the Koog framework.
         */
        public const val PREFIX: String = "ai.koog.openrouter"
    }
}
