package ai.koog.spring.prompt.executor.clients.anthropic

import ai.koog.spring.RetryConfigKoogProperties
import ai.koog.spring.prompt.executor.clients.KoogLlmClientProperties
import ai.koog.utils.lang.masked
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for the Koog library used for integrating with Anthropic LLM provider.
 * These properties are used in conjunction with the [ai.koog.spring.KoogAutoConfiguration] auto-configuration class to initialize and
 * configure respective client implementations.
 *
 * Configuration prefix: `ai.koog.anthropic`
 *
 * @param apiKey The API key used to authenticate requests to the provider's service
 * @param baseUrl The base URL of the provider's API endpoint. By default, it is set to `https://api.anthropic.com`
 */
@ConfigurationProperties(prefix = AnthropicKoogProperties.PREFIX)
public class AnthropicKoogProperties(
    public override val enabled: Boolean,
    public val apiKey: String,
    public override val baseUrl: String,
    public override val retry: RetryConfigKoogProperties? = null
) : KoogLlmClientProperties {
    /**
     * Companion object for the AnthropicKoogProperties class, providing constant values and
     * utilities associated with the configuration of Anthropic-related properties.
     */
    public companion object Companion {
        /**
         * Prefix constant used for configuration Anthropic-related properties in the Koog framework.
         */
        public const val PREFIX: String = "ai.koog.anthropic"
    }

    override fun toString(): String {
        return "AnthropicKoogProperties(apiKey='${apiKey.masked()}', baseUrl='$baseUrl', retry=$retry)"
    }
}
