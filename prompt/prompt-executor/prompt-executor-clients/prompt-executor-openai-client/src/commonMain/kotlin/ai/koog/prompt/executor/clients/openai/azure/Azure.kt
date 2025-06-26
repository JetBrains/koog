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
public fun AzureOpenAIClientSettings(
    resourceName: String,
    deploymentName: String,
    version: AzureOpenAIServiceVersion,
    timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig(),
): OpenAIClientSettings = OpenAIClientSettings(
    baseUrl = "https://$resourceName.openai.azure.com/openai/deployments/$deploymentName",
    timeoutConfig = timeoutConfig,
    chatCompletionsPath = "/chat/completions?api-version=${version.value}",
    embeddingsPath = "/embeddings?api-version=${version.value}",
)
