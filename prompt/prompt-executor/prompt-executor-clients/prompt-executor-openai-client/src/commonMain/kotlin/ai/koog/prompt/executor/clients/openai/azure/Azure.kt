package ai.koog.prompt.executor.clients.openai.azure

import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings

/**
 * Creates an instance of [OpenAIClientSettings] for Azure OpenAI client configuration.
 *
 * @param resourceName The name of the Azure OpenAI resource.
 * @param deploymentName The name of the deployment within the Azure OpenAI resource.
 * @param version The version of the Azure OpenAI Service to use.
 * @param timeoutConfig Configuration for connection timeouts, including request, connect, and socket timeouts.
 */
@Suppress("FunctionName")
public fun AzureOpenAIClientSettings(
    resourceName: String,
    deploymentName: String,
    version: AzureOpenAIServiceVersion,
    timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig(),
): OpenAIClientSettings = AzureOpenAIClientSettings(
    baseUrl = "https://$resourceName.openai.azure.com/openai/deployments/$deploymentName",
    version = version,
    timeoutConfig = timeoutConfig,
)

/**
 * Creates an instance of [OpenAIClientSettings] for Azure OpenAI client configuration.
 *
 * This function is a convenience method that allows you to specify the base URL directly,
 * along with the Azure OpenAI service version and connection timeout configuration.
 *
 * @param baseUrl The base URL for the Azure OpenAI service.
 * @param version The version of the Azure OpenAI Service to use.
 * @param timeoutConfig Configuration for connection timeouts, including request, connect, and socket timeouts.
 */
@Suppress("FunctionName")
public fun AzureOpenAIClientSettings(
    baseUrl: String,
    version: AzureOpenAIServiceVersion,
    timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig(),
): OpenAIClientSettings = OpenAIClientSettings(
    baseUrl = baseUrl,
    timeoutConfig = timeoutConfig,
    chatCompletionsPath = "/chat/completions?api-version=${version.value}",
    embeddingsPath = "/embeddings?api-version=${version.value}",
)
