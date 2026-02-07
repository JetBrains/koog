package ai.koog.prompt.executor.llms.all

import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.mistralai.MistralAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.azure.AzureOpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.azure.AzureOpenAIServiceVersion
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import io.ktor.client.HttpClient
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header

/**
 * Creates a `SingleLLMPromptExecutor` instance configured to use the OpenAI client.
 *
 * This method simplifies the setup process by creating an `OpenAILLMClient` with the provided API token
 * and wrapping it in a `SingleLLMPromptExecutor` to allow prompt execution with the OpenAI service.
 *
 * @param apiToken The API token used for authentication with the OpenAI API.
 * @return A new instance of `SingleLLMPromptExecutor` configured with the `OpenAILLMClient`.
 */
public fun simpleOpenAIExecutor(
    apiToken: String
): SingleLLMPromptExecutor = SingleLLMPromptExecutor(OpenAILLMClient(apiToken))

/**
 * Creates an instance of `SingleLLMPromptExecutor` with an `OpenAILLMClient` configured for Azure OpenAI.
 *
 * @param resourceName The name of the Azure OpenAI resource.
 * @param deploymentName The name of the deployment within the Azure OpenAI resource.
 * @param version The version of the Azure OpenAI Service to use.
 * @param apiToken The API token used for authentication with the Azure OpenAI service.
 * @return A new instance of `SingleLLMPromptExecutor` configured with the `OpenAILLMClient` for Azure OpenAI.
 */
public fun simpleAzureOpenAIExecutor(
    resourceName: String,
    deploymentName: String,
    version: AzureOpenAIServiceVersion,
    apiToken: String,
): SingleLLMPromptExecutor =
    SingleLLMPromptExecutor(OpenAILLMClient(apiToken, AzureOpenAIClientSettings(resourceName, deploymentName, version)))

/**
 * Creates an instance of `SingleLLMPromptExecutor` with an `OpenAILLMClient` configured for Azure OpenAI.
 *
 * @param baseUrl The base URL for the Azure OpenAI service.
 * @param version The version of the Azure OpenAI Service to use.
 * @param apiToken The API token used for authentication with the Azure OpenAI service.
 * @return A new instance of `SingleLLMPromptExecutor` configured with the `OpenAILLMClient` for Azure OpenAI.
 */
public fun simpleAzureOpenAIExecutor(
    baseUrl: String,
    version: AzureOpenAIServiceVersion,
    apiToken: String,
): SingleLLMPromptExecutor =
    SingleLLMPromptExecutor(OpenAILLMClient(apiToken, AzureOpenAIClientSettings(baseUrl, version)))

/**
 * Creates an instance of `SingleLLMPromptExecutor` with an `OpenAILLMClient` configured for Azure OpenAI.
 *
 * @param baseUrl The base URL for the Azure OpenAI service.
 * @param apiKey The API key (or Entra ID token) used for authentication with the Azure OpenAI service.
 * @param usesAzureEntraId Indicates whether the authentication uses Azure Entra ID token (true)
 * or `api-key` header (false). Defaults to true.
 * @return A new instance of `SingleLLMPromptExecutor` configured with the `OpenAILLMClient` for Azure OpenAI.
 */
public fun simpleAzureOpenAIExecutor(
    baseUrl: String,
    apiKey: String,
    usesAzureEntraId: Boolean = true
): SingleLLMPromptExecutor {
    if (usesAzureEntraId) {
        return SingleLLMPromptExecutor(
            OpenAILLMClient(
                apiKey,
                OpenAIClientSettings(baseUrl),
            )
        )
    }

    return SingleLLMPromptExecutor(
        OpenAILLMClient(
            "",
            OpenAIClientSettings(baseUrl),
            HttpClient {
                defaultRequest {
                    header("api-key", apiKey)
                }
            }
        ))
}

/**
 * Creates an instance of `SingleLLMPromptExecutor` with an `AnthropicLLMClient`.
 *
 * @param apiKey The API token used for authentication with the Anthropic LLM client.
 */
public fun simpleAnthropicExecutor(
    apiKey: String
): SingleLLMPromptExecutor = SingleLLMPromptExecutor(AnthropicLLMClient(apiKey))

/**
 * Creates an instance of `SingleLLMPromptExecutor` with an `OpenRouterLLMClient`.
 *
 * @param apiKey The API token used for authentication with the OpenRouter API.
 */
public fun simpleOpenRouterExecutor(
    apiKey: String
): SingleLLMPromptExecutor = SingleLLMPromptExecutor(OpenRouterLLMClient(apiKey))

/**
 * Creates an instance of `SingleLLMPromptExecutor` with an `GoogleLLMClient`.
 *
 * @param apiKey The API token used for authentication with the Google AI service.
 */
public fun simpleGoogleAIExecutor(
    apiKey: String
): SingleLLMPromptExecutor = SingleLLMPromptExecutor(GoogleLLMClient(apiKey))

/**
 * Creates an instance of `SingleLLMPromptExecutor` with an `OllamaClient`.
 *
 * @param baseUrl url used to access Ollama server.
 */
public fun simpleOllamaAIExecutor(
    baseUrl: String = "http://localhost:11434"
): SingleLLMPromptExecutor = SingleLLMPromptExecutor(OllamaClient(baseUrl))

/**
 * Creates an instance of `SingleLLMPromptExecutor` with a `MistralAILLMClient`.
 *
 * @param apiKey The API token used for authentication with the Mistral AI provider.
 */
public fun simpleMistralAIExecutor(apiKey: String): SingleLLMPromptExecutor =
    SingleLLMPromptExecutor(MistralAILLMClient(apiKey))
