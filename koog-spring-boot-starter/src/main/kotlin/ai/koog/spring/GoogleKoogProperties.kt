package ai.koog.spring

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for the Koog library used for integrating with Google LLM provider.
 * These properties are used in conjunction with the [KoogAutoConfiguration] auto-configuration class to initialize and
 * configure respective client implementations.
 *
 * Configuration prefix: `ai.koog.google`
 *
 * @param apiKey The API key used to authenticate requests to the provider's service
 * @param baseUrl The base URL of the provider's API endpoint. By default, it is set to `https://generativelanguage.googleapis.com`
 */
@ConfigurationProperties(prefix = GoogleKoogProperties.PREFIX)
public class GoogleKoogProperties(
    public val apiKey: String = "",
    public val baseUrl: String = "https://generativelanguage.googleapis.com",
    public val retry: RetryConfigKoogProperties? = null
) {
    /**
     * Companion object for the GoogleKoogProperties class, providing constant values and
     * utilities associated with the configuration of Google-related properties.
     */
    public companion object Companion {
        /**
         * Prefix constant used for configuration Google-related properties in the Koog framework.
         */
        public const val PREFIX: String = "ai.koog.google"
    }
}
