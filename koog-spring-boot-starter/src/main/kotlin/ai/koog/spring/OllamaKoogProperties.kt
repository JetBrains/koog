package ai.koog.spring

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for the Koog library used for integrating with Ollama LLM provider.
 * These properties are used in conjunction with the [KoogAutoConfiguration] auto-configuration class to initialize and
 * configure respective client implementations.
 *
 * Configuration prefix: `ai.koog.ollama`
 *
 * @param baseUrl The base URL of the provider's API endpoint. By default, it is set to `http://localhost:11434`
 */
@ConfigurationProperties(prefix = OllamaKoogProperties.PREFIX)
public class OllamaKoogProperties(
    public val baseUrl: String = "http://localhost:11434",
    public val retry: RetryConfigKoogProperties? = null
) {
    /**
     * Companion object for the OllamaKoogProperties class, providing constant values and
     * utilities associated with the configuration of Ollama-related properties.
     */
    public companion object Companion {
        /**
         * Prefix constant used for configuration Ollama-related properties in the Koog framework.
         */
        public const val PREFIX: String = "ai.koog.ollama"
    }
}
