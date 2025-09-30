package ai.koog.spring.prompt.executor.clients.ollama

import ai.koog.spring.RetryConfigKoogProperties
import ai.koog.spring.prompt.executor.clients.KoogLlmClientProperties
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for the Koog library used for integrating with Ollama LLM provider.
 * These properties are used in conjunction with the [ai.koog.spring.KoogAutoConfiguration] auto-configuration class to initialize and
 * configure respective client implementations.
 *
 * Configuration prefix: `ai.koog.ollama`
 *
 * @param baseUrl The base URL of the provider's API endpoint. By default, it is set to `http://localhost:11434`
 */
@ConfigurationProperties(prefix = OllamaKoogProperties.PREFIX)
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

    override fun toString(): String {
        return "OllamaKoogProperties(enabled=$enabled, baseUrl='$baseUrl', retry=$retry)"
    }
}
