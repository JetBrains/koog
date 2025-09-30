package ai.koog.spring.prompt.executor.clients.google

import ai.koog.spring.RetryConfigKoogProperties
import ai.koog.spring.prompt.executor.clients.KoogLlmClientProperties
import ai.koog.utils.lang.masked
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for the Koog library used for integrating with Google LLM provider.
 * These properties are used in conjunction with the [GoogleLLMAutoConfiguration] auto-configuration class to initialize and
 * configure respective client implementations.
 *
 * Configuration prefix: `ai.koog.google`
 *
 * @param apiKey The API key used to authenticate requests to the provider's service
 * @param baseUrl The base URL of the provider's API endpoint. By default, it is set to `https://generativelanguage.googleapis.com`
 */
@ConfigurationProperties(prefix = GoogleKoogProperties.PREFIX)
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
