package ai.koog.spring.prompt.executor.clients.mistralai

import ai.koog.spring.RetryConfigKoogProperties
import ai.koog.spring.prompt.executor.clients.KoogLlmClientProperties
import ai.koog.utils.lang.masked
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration class for MistralAI settings in the Koog framework.
 * This class defines properties required to configure and use MistralAI-related services, such as API keys,
 * base URLs for the services, and optional retry configurations.
 *
 * The class is annotated with `@ConfigurationProperties` to bind its fields to configuration file properties
 * prefixed with `ai.koog.mistral`.
 *
 * @property enabled Determines if the MistralAI client is enabled.
 * @property apiKey The API key required to authenticate requests to the MistralAI API.
 * @property baseUrl The base URL for accessing the MistralAI API.
 * @property retry Optional retry configuration settings, such as maximum attempts, delays, and backoff strategies.
 *
 * This configuration is used in `MistralAILLMAutoConfiguration` to set up the MistralAI client and related beans.
 */
@ConfigurationProperties(prefix = MistralAIKoogProperties.PREFIX, ignoreUnknownFields = true)
public class MistralAIKoogProperties(
    public override val enabled: Boolean,
    public val apiKey: String,
    public override val baseUrl: String,
    public override val retry: RetryConfigKoogProperties? = null
) : KoogLlmClientProperties {
    /**
     * Companion object for the MistralAIKoogProperties class, providing constant values and
     * utilities associated with the configuration of MistralAI-related properties.
     */
    public companion object {
        /**
         * Prefix constant used for configuration MistralAI-related properties in the Koog framework.
         */
        public const val PREFIX: String = "ai.koog.mistral"
    }

    /**
     * Returns a string representation of the `MistralAIKoogProperties` object.
     * The string includes details about the `enabled` status, masked `apiKey`,
     * `baseUrl`, and `retry` configuration.
     *
     * @return A string summarizing the `MistralAIKoogProperties` object's state.
     */
    override fun toString(): String {
        return "MistralAIKoogProperties(enabled=$enabled, apiKey='$${apiKey.masked()}', baseUrl='$baseUrl', retry=$retry)"
    }
}
