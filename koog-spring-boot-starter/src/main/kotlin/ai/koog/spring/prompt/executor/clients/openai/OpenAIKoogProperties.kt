package ai.koog.spring.prompt.executor.clients.openai

import ai.koog.spring.RetryConfigKoogProperties
import ai.koog.spring.prompt.executor.clients.KoogLlmClientProperties
import ai.koog.utils.lang.masked
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration class for OpenAI settings in the Koog framework.
 * This class defines properties required to configure and use OpenAI-related services, such as API keys,
 * base URLs for the services, and optional retry configurations.
 *
 * The class is annotated with `@ConfigurationProperties` to bind its fields to configuration file properties
 * prefixed with `ai.koog.openai`.
 *
 * @property enabled Determines if the OpenAI client is enabled.
 * @property apiKey The API key required to authenticate requests to the OpenAI API.
 * @property baseUrl The base URL for accessing the OpenAI API.
 * @property retry Optional retry configuration settings, such as maximum attempts, delays, and backoff strategies.
 *
 * This configuration is used in `OpenAILLMAutoConfiguration` to set up the OpenAI client and related beans.
 */
@ConfigurationProperties(prefix = OpenAIKoogProperties.PREFIX, ignoreUnknownFields = true)
public class OpenAIKoogProperties(
    public override val enabled: Boolean,
    public val apiKey: String,
    public override val baseUrl: String,
    public override val retry: RetryConfigKoogProperties? = null
) : KoogLlmClientProperties {
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

    /**
     * Returns a string representation of the `OpenAIKoogProperties` object.
     * The string includes details about the `enabled` status, masked `apiKey`,
     * `baseUrl`, and `retry` configuration.
     *
     * @return A string summarizing the `OpenAIKoogProperties` object's state.
     */
    override fun toString(): String {
        return "OpenAIKoogProperties(enabled=$enabled, apiKey='$${apiKey.masked()}', baseUrl='$baseUrl', retry=$retry)"
    }
}
