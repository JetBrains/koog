package ai.koog.spring.prompt.executor.clients

import ai.koog.spring.RetryConfigKoogProperties

/**
 * Interface representing configuration properties for a Koog LLM Client.
 *
 * This interface is intended to provide the necessary configuration required to set up a LLM Client.
 * It includes options for enabling the client, specifying the base URL, and defining retry configurations.
 *
 * @param enabled Indicates whether the LLM client is enabled.
 * @param baseUrl Specifies the base URL for the LLM client.
 * @param retry Defines the retry configuration for the LLM client using [RetryConfigKoogProperties].
 */
public interface KoogLlmClientProperties {
    public val enabled: Boolean
    public val baseUrl: String
    public val retry: RetryConfigKoogProperties?
}
