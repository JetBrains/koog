package ai.koog.spring.prompt.executor.clients.requesty

import ai.koog.spring.RetryConfigKoogProperties
import ai.koog.spring.prompt.executor.clients.KoogLlmClientProperties
import ai.koog.utils.lang.masked
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties class for Requesty integration within the Koog framework.
 *
 * This class defines configuration options required for connecting to the Requesty service
 * via the Koog framework. It includes parameters such as API key, base URL, enabling or disabling
 * the integration, and retry configuration for handling API requests.
 *
 * When properly configured in the application properties using the defined prefix, this class
 * allows seamless integration with Requesty's LLM services.
 *
 * Configuration prefix: `ai.koog.requesty`
 *
 * @property enabled Specifies whether the Requesty integration is enabled. This can be toggled
 * via the property `ai.koog.requesty.enabled`.
 * @property apiKey The API key used for authenticating requests to the Requesty service.
 * This must be provided through the property `ai.koog.requesty.api-key`.
 * @property baseUrl The base URL of the Requesty API endpoint, configurable via the
 * property `ai.koog.requesty.base-url`. Defaults to the service's official API URL.
 * @property retry An optional retry configuration for handling failed API requests.
 * This can be set using sub-properties under `ai.koog.requesty.retry`.
 */
@ConfigurationProperties(prefix = RequestyKoogProperties.PREFIX, ignoreUnknownFields = true)
public class RequestyKoogProperties(
    public override val enabled: Boolean,
    public val apiKey: String,
    public override val baseUrl: String,
    public override val retry: RetryConfigKoogProperties? = null
) : KoogLlmClientProperties {
    /**
     * Companion object for the [RequestyKoogProperties] class, providing constant values and
     * utilities associated with the configuration of Requesty-related properties.
     */
    public companion object Companion {
        /**
         * Prefix constant used for configuration Requesty-related properties in the Koog framework.
         */
        public const val PREFIX: String = "ai.koog.requesty"
    }

    /**
     * Converts the [RequestyKoogProperties] instance to its string representation.
     * Sensitive information, such as the API key, is masked to ensure security.
     *
     * @return A string representation of the [RequestyKoogProperties] object.
     */
    override fun toString(): String {
        return "RequestyKoogProperties(enabled=$enabled, apiKey='${apiKey.masked()}', baseUrl='$baseUrl', retry=$retry)"
    }
}
