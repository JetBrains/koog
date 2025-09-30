package ai.koog.spring.prompt.executor.clients.google

import ai.koog.spring.RetryConfigKoogProperties
import ai.koog.spring.prompt.executor.clients.KoogLlmClientProperties
import ai.koog.utils.lang.masked
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for integrating with Google's LLM services in the Koog framework.
 *
 * This class provides the necessary settings for enabling and configuring access
 * to Google's LLM API, including authentication and retry behavior.
 * The configuration is mapped using the prefix `ai.koog.google`.
 *
 * Parameters:
 * @param enabled Indicates whether the Google LLM integration is enabled.
 * @param apiKey The API key used for authenticating requests to Google's services.
 * @param baseUrl The base URL of the Google LLM API.
 * @param retry Optional configuration for retrying failed API calls.
 *
 * Usage:
 * These properties are automatically bound to the Spring environment when specified
 * in application configuration (e.g., `application.yml` or `application.properties`).
 *
 * Example configuration snippet in `application.yml` or `application.properties`:
 * ```properties
 * ai.koog.google.enabled=true
 * ai.koog.google.api-key=your-google-api-key
 * ai.koog.google.base-url=https://api.google.com/llm
 * ai.koog.google.retry.enabled=true
 * ai.koog.google.retry.max-attempts=3
 * ai.koog.google.retry.initial-delay=2s
 * ai.koog.google.retry.max-delay=10s
 * ai.koog.google.retry.backoff-multiplier=2.0
 * ai.koog.google.retry.jitter-factor=0.5
 * ```
 *
 * Advanced Features:
 * - The retry configuration supports customizable retries for handling transient failures.
 * - Dedicated masking utility ensures that sensitive information, such as the API key, is
 *   not exposed when serialized or logged.
 *
 * This class is primarily used in conjunction with the `GoogleLLMAutoConfiguration` auto-configuration
 * class to initialize and configure the necessary beans for interacting with Google's LLM API.
 *
 * For more details on retry behavior, refer to the `RetryConfigKoogProperties` class.
 * For shared configuration attributes, see the `KoogLlmClientProperties` interface.
 *
 *  @property enabled Enables or disables the Google LLM integration.
 *  @property apiKey The key required for authenticating with the API.
 *  @property baseUrl URL endpoint for the Google LLM API.
 *  @property retry Defines retry behavior, such as maximum attempts and delays between retries.
 */
@ConfigurationProperties(prefix = GoogleKoogProperties.PREFIX, ignoreUnknownFields = true)
public class GoogleKoogProperties(
    public override val enabled: Boolean,
    public val apiKey: String,
    public override val baseUrl: String,
    public override val retry: RetryConfigKoogProperties? = null
) : KoogLlmClientProperties {
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

    /**
     * Returns a string representation of the GoogleKoogProperties object.
     *
     * The resulting string includes details about the object's properties such as
     * `enabled`, `apiKey` (with sensitive information masked), `baseUrl`, and `retry`.
     *
     * @return A string representation of the GoogleKoogProperties object.
     */
    override fun toString(): String {
        return "GoogleKoogProperties(enabled=$enabled, apiKey='${apiKey.masked()}', baseUrl='$baseUrl', retry=$retry)"
    }
}
