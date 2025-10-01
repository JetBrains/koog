package ai.koog.spring.prompt.executor.clients.ollama

import ai.koog.spring.RetryConfigKoogProperties
import ai.koog.spring.prompt.executor.clients.KoogLlmClientProperties
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for the Ollama integration in the Koog framework.
 *
 * This class defines properties that control the connection and behavior when interacting
 * with the Ollama Large Language Model (LLM) service.
 *
 * These properties are typically configured in the application properties or YAML file.
 *
 * The configuration prefix for these properties is defined as `ai.koog.ollama`.
 * It is used to map these properties in the application configuration file.
 *
 * This class is designed to work along with the `OllamaLLMAutoConfiguration` class to
 * automatically initialize and configure the required beans for the Ollama client and executor.
 *
 * @property enabled Indicates whether the Ollama integration is enabled.
 * @property baseUrl The URL of the API endpoint for Ollama service.
 * @property retry The retry settings for handling request failures.
 */
@ConfigurationProperties(prefix = OllamaKoogProperties.PREFIX, ignoreUnknownFields = true)
public class OllamaKoogProperties(
    public override val enabled: Boolean,
    public override val baseUrl: String,
    public override val retry: RetryConfigKoogProperties? = null
) : KoogLlmClientProperties {
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

    /**
     * Returns a string representation of the `OllamaKoogProperties` object.
     *
     * The string includes values for the `enabled`, `baseUrl`, and `retry` properties
     * to provide a comprehensive overview of the configuration state of the object.
     *
     * @return a string describing the current state of the `OllamaKoogProperties` instance.
     */
    override fun toString(): String {
        return "OllamaKoogProperties(enabled=$enabled, baseUrl='$baseUrl', retry=$retry)"
    }
}
