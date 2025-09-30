package ai.koog.spring.prompt.executor.clients.openrouter

import ai.koog.spring.RetryConfigKoogProperties
import ai.koog.spring.prompt.executor.clients.KoogLlmClientProperties
import ai.koog.utils.lang.masked
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties class for OpenRouter integration within the Koog framework.
 *
 * This class defines configuration options required for connecting to the OpenRouter service
 * via the Koog framework. It includes parameters such as API key, base URL, enabling or disabling
 * the integration, and retry configuration for handling API requests.
 *
 * When properly configured in the application properties using the defined prefix, this class
 * allows seamless integration with OpenRouter's LLM services.
 *
 * Configuration prefix: `ai.koog.openrouter`
 *
 * @property enabled Specifies whether the OpenRouter integration is enabled. This can be toggled
 * via the property `ai.koog.openrouter.enabled`.
 * @property apiKey The API key used for authenticating requests to the OpenRouter service.
 * This must be provided through the property `ai.koog.openrouter.api-key`.
 * @property baseUrl The base URL of the OpenRouter API endpoint, configurable via the
 * property `ai.koog.openrouter.base-url`. Defaults to the service's official API URL.
 * @property retry An optional retry configuration for handling failed API requests.
 * This can be set using sub-properties under `ai.koog.openrouter.retry`.
 */
@ConfigurationProperties(prefix = OpenRouterKoogProperties.PREFIX, ignoreUnknownFields = true)
public class OpenRouterKoogProperties(
    public override val enabled: Boolean,
    public val apiKey: String,
    public override val baseUrl: String,
    public override val retry: RetryConfigKoogProperties? = null
) : KoogLlmClientProperties {
    /**
     * Companion object for the [OpenRouterKoogProperties] class, providing constant values and
     * utilities associated with the configuration of OpenRouter-related properties.
     */
    public companion object Companion {
        /**
         * Prefix constant used for configuration OpenRouter-related properties in the Koog framework.
         */
        public const val PREFIX: String = "ai.koog.openrouter"
    }

    /**
     * Converts the [OpenRouterKoogProperties] instance to its string representation.
     * Sensitive information, such as the API key, is masked to ensure security.
     *
     * @return A string representation of the [OpenRouterKoogProperties] object.
     */
    override fun toString(): String {
        return "OpenRouterKoogProperties(enabled=$enabled, apiKey='${apiKey.masked()}', baseUrl='$baseUrl', retry=$retry)"
    }
}
