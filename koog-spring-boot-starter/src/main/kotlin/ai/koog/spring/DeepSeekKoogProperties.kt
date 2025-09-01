package ai.koog.spring

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for the Koog library used for integrating with DeepSeek LLM provider.
 * These properties are used in conjunction with the [KoogAutoConfiguration] auto-configuration class to initialize and
 * configure respective client implementations.
 *
 * Configuration prefix: `ai.koog.deepseek`
 *
 * @param apiKey The API key used to authenticate requests to the provider's service
 * @param baseUrl The base URL of the provider's API endpoint. By default, it is set to `https://api.deepseek.com`
 */
@ConfigurationProperties(prefix = DeepSeekKoogProperties.PREFIX)
public class DeepSeekKoogProperties(
    public val apiKey: String = "",
    public val baseUrl: String = "https://api.deepseek.com"
) {
    /**
     * Companion object for the DeepSeekKoogProperties class, providing constant values and
     * utilities associated with the configuration of DeepSeek-related properties.
     */
    public companion object Companion {
        /**
         * Prefix constant used for configuration DeepSeek-related properties in the Koog framework.
         */
        public const val PREFIX: String = "ai.koog.deepseek"
    }
}
