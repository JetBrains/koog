package ai.koog.spring.prompt.executor.clients.openai

import ai.koog.spring.RetryConfigKoogProperties
import ai.koog.spring.prompt.executor.clients.KoogLlmClientProperties
import ai.koog.utils.lang.masked
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for the Koog library used for integrating with OpenAI LLM provider.
 * These properties are used in conjunction with the [ai.koog.spring.KoogAutoConfiguration] auto-configuration class to initialize and
 * configure respective client implementations.
 *
 * Configuration prefix: `ai.koog.openai`
 *
 * @param apiKey The API key used to authenticate requests to the provider's service
 * @param baseUrl The base URL of the provider's API endpoint. By default, it is set to `https://api.openai.com`
 */
@ConfigurationProperties(prefix = OpenAIKoogProperties.PREFIX)
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
