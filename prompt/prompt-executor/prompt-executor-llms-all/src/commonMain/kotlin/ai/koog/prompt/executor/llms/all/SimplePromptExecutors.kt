@file:JvmMultifileClass
@file:JvmName("SimplePromptExecutors")

package ai.koog.prompt.executor.llms.all

import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.mistralai.MistralAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.azure.AzureOpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.azure.AzureOpenAIServiceVersion
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName

/**
 * Creates a `SingleLLMPromptExecutor` instance configured to use the OpenAI client.
 *
 * @param apiToken The API token used for authentication with the OpenAI API.
 * @param httpClientFactory Factory used to create the underlying HTTP client.
 * @return A new instance of `SingleLLMPromptExecutor` configured with the `OpenAILLMClient`.
 */
public fun simpleOpenAIExecutor(
    apiToken: String,
    httpClientFactory: KoogHttpClient.Factory,
): SingleLLMPromptExecutor = SingleLLMPromptExecutor(
    OpenAILLMClient(apiKey = apiToken, httpClientFactory = httpClientFactory)
)

/**
 * Creates an instance of `SingleLLMPromptExecutor` with an `OpenAILLMClient` configured for Azure OpenAI.
 *
 * @param resourceName The name of the Azure OpenAI resource.
 * @param deploymentName The name of the deployment within the Azure OpenAI resource.
 * @param version The version of the Azure OpenAI Service to use.
 * @param apiToken The API token used for authentication with the Azure OpenAI service.
 * @param httpClientFactory Factory used to create the underlying HTTP client.
 */
public fun simpleAzureOpenAIExecutor(
    resourceName: String,
    deploymentName: String,
    version: AzureOpenAIServiceVersion,
    apiToken: String,
    httpClientFactory: KoogHttpClient.Factory,
): SingleLLMPromptExecutor = SingleLLMPromptExecutor(
    OpenAILLMClient(
        apiKey = apiToken,
        settings = AzureOpenAIClientSettings(resourceName, deploymentName, version),
        httpClientFactory = httpClientFactory,
    )
)

/**
 * Creates an instance of `SingleLLMPromptExecutor` with an `OpenAILLMClient` configured for Azure OpenAI.
 *
 * @param baseUrl The base URL for the Azure OpenAI service.
 * @param version The version of the Azure OpenAI Service to use.
 * @param apiToken The API token used for authentication with the Azure OpenAI service.
 * @param httpClientFactory Factory used to create the underlying HTTP client.
 */
public fun simpleAzureOpenAIExecutor(
    baseUrl: String,
    version: AzureOpenAIServiceVersion,
    apiToken: String,
    httpClientFactory: KoogHttpClient.Factory,
): SingleLLMPromptExecutor = SingleLLMPromptExecutor(
    OpenAILLMClient(
        apiKey = apiToken,
        settings = AzureOpenAIClientSettings(baseUrl, version),
        httpClientFactory = httpClientFactory,
    )
)

/**
 * Creates an instance of `SingleLLMPromptExecutor` with an `AnthropicLLMClient`.
 *
 * @param apiKey The API token used for authentication with the Anthropic LLM client.
 * @param httpClientFactory Factory used to create the underlying HTTP client.
 */
public fun simpleAnthropicExecutor(
    apiKey: String,
    httpClientFactory: KoogHttpClient.Factory,
): SingleLLMPromptExecutor = SingleLLMPromptExecutor(
    AnthropicLLMClient(apiKey = apiKey, httpClientFactory = httpClientFactory)
)

/**
 * Creates an instance of `SingleLLMPromptExecutor` with an `OpenRouterLLMClient`.
 *
 * @param apiKey The API token used for authentication with the OpenRouter API.
 * @param httpClientFactory Factory used to create the underlying HTTP client.
 */
public fun simpleOpenRouterExecutor(
    apiKey: String,
    httpClientFactory: KoogHttpClient.Factory,
): SingleLLMPromptExecutor = SingleLLMPromptExecutor(
    OpenRouterLLMClient(apiKey = apiKey, httpClientFactory = httpClientFactory)
)

/**
 * Creates an instance of `SingleLLMPromptExecutor` with an `GoogleLLMClient`.
 *
 * @param apiKey The API token used for authentication with the Google AI service.
 * @param httpClientFactory Factory used to create the underlying HTTP client.
 */
public fun simpleGoogleAIExecutor(
    apiKey: String,
    httpClientFactory: KoogHttpClient.Factory,
): SingleLLMPromptExecutor = SingleLLMPromptExecutor(
    GoogleLLMClient(apiKey = apiKey, httpClientFactory = httpClientFactory)
)

/**
 * Creates an instance of `SingleLLMPromptExecutor` with an `OllamaClient`.
 *
 * @param baseUrl url used to access Ollama server.
 * @param httpClientFactory Factory used to create the underlying HTTP client.
 */
public fun simpleOllamaAIExecutor(
    baseUrl: String = "http://localhost:11434",
    httpClientFactory: KoogHttpClient.Factory,
): SingleLLMPromptExecutor = SingleLLMPromptExecutor(
    OllamaClient(baseUrl = baseUrl, httpClientFactory = httpClientFactory)
)

/**
 * Creates an instance of `SingleLLMPromptExecutor` with a `MistralAILLMClient`.
 *
 * @param apiKey The API token used for authentication with the Mistral AI provider.
 * @param httpClientFactory Factory used to create the underlying HTTP client.
 */
public fun simpleMistralAIExecutor(
    apiKey: String,
    httpClientFactory: KoogHttpClient.Factory,
): SingleLLMPromptExecutor = SingleLLMPromptExecutor(
    MistralAILLMClient(apiKey = apiKey, httpClientFactory = httpClientFactory)
)
