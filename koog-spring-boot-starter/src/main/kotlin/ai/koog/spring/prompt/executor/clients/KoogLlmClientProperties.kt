package ai.koog.spring.prompt.executor.clients

import ai.koog.spring.RetryConfigKoogProperties

/**
 * Configuration properties for the Koog library used for integrating with DeepSeek LLM provider.
 * These properties are used in conjunction with the [KoogAutoConfiguration] auto-configuration class to initialize and
 * configure respective client implementations.
 *
 * Configuration prefix: `ai.koog.deepseek`
 *
 * @param apiKey The API key used to authenticate requests to the provider's service
 * @param baseUrl The base URL of the provider's API endpoint. By default, it is set to `https://api.deepseek.com`
 */
public interface KoogLlmClientProperties {
    public val enabled: Boolean
    public val baseUrl: String
    public val retry: RetryConfigKoogProperties?
}
