package ai.koog.spring.prompt.executor.clients.anthropic

import ai.koog.spring.RetryConfigKoogProperties
import ai.koog.spring.prompt.executor.clients.KoogLlmClientProperties
import ai.koog.utils.lang.masked
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for configuring Anthropic-related clients in the Koog framework.
 *
 * This class allows defining settings necessary for integrating with the Anthropic LLM (Large Language Model)
 * client. It extends [KoogLlmClientProperties] to include common LLM client configurations such as `enabled`,
 * `baseUrl`, and retry options. Additionally, it includes the `apiKey` property specific to the Anthropic client.
 *
 * The properties are bound to the configuration prefix defined by [AnthropicKoogProperties.PREFIX], which is
 * "ai.koog.anthropic". This allows configuring the client via property files in a Spring Boot application.
 *
 * @param enabled Indicates whether the Anthropic client is enabled. If `false`, the client will not be configured.
 * @param apiKey The API key used to authenticate requests to the Anthropic API.
 * @param baseUrl The base URL of the Anthropic API for sending requests.
 * @param retry Retry configuration for the client in case of failed or timeout requests. This is optional.
 */
@ConfigurationProperties(prefix = AnthropicKoogProperties.PREFIX, ignoreUnknownFields = true)
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

    /**
     * Provides a string representation of the `AnthropicKoogProperties` instance, including
     * the masked API key, base URL, and retry configuration.
     *
     * @return A string that represents the current state of the `AnthropicKoogProperties` object.
     */
    override fun toString(): String {
        return "AnthropicKoogProperties(apiKey='${apiKey.masked()}', baseUrl='$baseUrl', retry=$retry)"
    }
}
