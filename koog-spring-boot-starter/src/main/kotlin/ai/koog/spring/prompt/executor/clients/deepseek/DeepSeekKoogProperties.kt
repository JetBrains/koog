package ai.koog.spring.prompt.executor.clients.deepseek

import ai.koog.spring.RetryConfigKoogProperties
import ai.koog.spring.prompt.executor.clients.KoogLlmClientProperties
import ai.koog.utils.lang.masked
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for the Koog library used for integrating with DeepSeek LLM provider.
 * These properties are used in conjunction with the [DeepSeekLLMAutoConfiguration] auto-configuration class to initialize and
 * configure respective client implementations.
 *
 * Configuration prefix: `ai.koog.deepseek`
 *
 * @param apiKey The API key used to authenticate requests to the provider's service
 * @param baseUrl The base URL of the provider's API endpoint. By default, it is set to `https://api.deepseek.com`
 */
@ConfigurationProperties(prefix = DeepSeekKoogProperties.PREFIX)
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
