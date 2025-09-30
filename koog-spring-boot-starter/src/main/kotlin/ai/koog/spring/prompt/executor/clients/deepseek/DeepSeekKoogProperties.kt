package ai.koog.spring.prompt.executor.clients.deepseek

import ai.koog.spring.RetryConfigKoogProperties
import ai.koog.spring.prompt.executor.clients.KoogLlmClientProperties
import ai.koog.spring.prompt.executor.clients.deepseek.DeepSeekKoogProperties.Companion.PREFIX
import ai.koog.utils.lang.masked
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties class for DeepSeek LLM provider integration within the Koog framework.
 *
 * This class is used to define and manage application-level configuration parameters for connecting
 * to the DeepSeek provider. It includes properties such as API key, base URL, and optional retry settings.
 *
 * The properties are auto-configured via Spring Boot's `@ConfigurationProperties` using the `ai.koog.deepseek` prefix.
 *
 * Implements the [KoogLlmClientProperties] interface, which provides base attributes for all LLM client property configurations.
 *
 * Properties from this class are typically consumed by auto-configuration classes, such as [DeepSeekLLMAutoConfiguration],
 * to initialize and configure the necessary beans for working with the DeepSeek API.
 *
 * @property enabled Indicates whether DeepSeek API integration is enabled (true or false).
 * @property apiKey An API key string required to authenticate requests to the DeepSeek external service.
 * @property baseUrl The base URL endpoint for DeepSeek API calls.
 * @property retry Optional retry configuration for API requests, represented by [RetryConfigKoogProperties].
 */
@ConfigurationProperties(prefix = PREFIX, ignoreUnknownFields = true)
public class DeepSeekKoogProperties(
    public override val enabled: Boolean,
    public val apiKey: String,
    public override val baseUrl: String,
    public override val retry: RetryConfigKoogProperties? = null
) : KoogLlmClientProperties {
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

    /**
     * Returns a string representation of the DeepSeekKoogProperties object.
     * The string includes information about the `enabled` status, a masked representation of the `apiKey`,
     * the `baseUrl`, and the `retry` configuration.
     *
     * @return A string summarizing the current configuration properties.
     */
    override fun toString(): String {
        return "DeepSeekKoogProperties(enabled=$enabled, apiKey='$${apiKey.masked()}', baseUrl='$baseUrl', retry=$retry)"
    }
}
