package ai.koog.spring.prompt.executor.clients.anthropic

import ai.koog.spring.RetryConfigKoogProperties
import ai.koog.spring.prompt.executor.clients.KoogLlmClientProperties
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for configuring Anthropic-related clients in the Koog framework.
 *
 * This class allows defining settings necessary for integrating with the Anthropic LLM (Large Language Model)
 * client. It implements [KoogLlmClientProperties] and includes common LLM client configurations such as `enabled`,
 * `baseUrl`, and retry options. Additionally, it includes the `apiKey` property specific to the Anthropic client.
 *
 * The properties are bound to the configuration prefix defined by [AnthropicKoogProperties.PREFIX], which is
 * `ai.koog.anthropic`. This allows configuring the client via property files in a Spring Boot application.
 *
 * @property enabled Indicates whether the Anthropic client is enabled. If `false`, the client will not be configured.
 * @property apiKey The API key used to authenticate requests to the Anthropic API.
 * @property baseUrl The base URL of the Anthropic API for sending requests.
 * @property retry Retry configuration for the client in case of failed or timeout requests. This is optional.
 */
@ConfigurationProperties(prefix = AnthropicKoogProperties.PREFIX, ignoreUnknownFields = true)
public class AnthropicKoogProperties(
    public override val enabled: Boolean,
    public val apiKey: String,
    public override val baseUrl: String,
    public override val retry: RetryConfigKoogProperties? = null
) : KoogLlmClientProperties {
    /**
     * Companion object providing constant value associated with the configuration of Anthropic-related properties.
     */
    public companion object Companion {
        /**
         * Prefix constant used for configuration Anthropic-related properties in the Koog framework.
         */
        public const val PREFIX: String = "ai.koog.anthropic"
    }

    /**
     * Returns a string representation of the `AnthropicKoogProperties` object.
     *
     * The representation includes the state of its properties: `enabled`, `apiKey`(masked), `baseUrl`, and `retry`.
     *
     * @return A string containing the property values of the `AnthropicKoogProperties` object.
     */
    override fun toString(): String {
        return "AnthropicKoogProperties(enabled=$enabled, apiKey='$apiKey', baseUrl='$baseUrl', retry=$retry)"
    }
}
