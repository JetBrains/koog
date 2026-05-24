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
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.llm.LLMProvider
import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName

/**
 * Creates a `MultiLLMPromptExecutor` instance configured to use the OpenAI client.
 *
 * @param apiToken The API token used for authentication with the OpenAI API.
 * @param httpClientFactory Factory used to create the underlying HTTP client.
 * @return A new instance of `MultiLLMPromptExecutor` configured with the `OpenAILLMClient`.
 */
public fun simpleOpenAIExecutor(
    apiToken: String,
    httpClientFactory: KoogHttpClient.Factory,
): MultiLLMPromptExecutor = MultiLLMPromptExecutor(
    LLMProvider.OpenAI to OpenAILLMClient(apiKey = apiToken, httpClientFactory = httpClientFactory)
)

/**
 * Creates an instance of `MultiLLMPromptExecutor` with an `OpenAILLMClient` configured for Azure OpenAI.
 *
 * @param resourceName The name of the Azure OpenAI resource.
 * @param deploymentName The name of the deployment within the Azure OpenAI resource.
 * @param version The version of the Azure OpenAI Service to use.
 * @param apiToken The API token used for authentication with the Azure OpenAI service.
 * @param httpClientFactory Factory used to create the underlying HTTP client.
 * @return A new instance of `MultiLLMPromptExecutor` configured with the `OpenAILLMClient` for Azure OpenAI.
 */
public fun simpleAzureOpenAIExecutor(
    resourceName: String,
    deploymentName: String,
    version: AzureOpenAIServiceVersion,
    apiToken: String,
    httpClientFactory: KoogHttpClient.Factory
): MultiLLMPromptExecutor = MultiLLMPromptExecutor(
    LLMProvider.OpenAI to OpenAILLMClient(
        apiToken,
        AzureOpenAIClientSettings(resourceName, deploymentName, version),
        httpClientFactory = httpClientFactory
    )
)

/**
 * Creates an instance of `MultiLLMPromptExecutor` with an `OpenAILLMClient` configured for Azure OpenAI.
 *
 * @param baseUrl The base URL for the Azure OpenAI service.
 * @param version The version of the Azure OpenAI Service to use.
 * @param apiToken The API token used for authentication with the Azure OpenAI service.
 * @param httpClientFactory Factory used to create the underlying HTTP client.
 * @return A new instance of `MultiLLMPromptExecutor` configured with the `OpenAILLMClient` for Azure OpenAI.
 */
public fun simpleAzureOpenAIExecutor(
    baseUrl: String,
    version: AzureOpenAIServiceVersion,
    apiToken: String,
    httpClientFactory: KoogHttpClient.Factory,
): MultiLLMPromptExecutor = MultiLLMPromptExecutor(
    LLMProvider.OpenAI to OpenAILLMClient(
        apiKey = apiToken,
        settings = AzureOpenAIClientSettings(baseUrl, version),
        httpClientFactory = httpClientFactory,
    ),
)

/**
 * Creates an instance of `MultiLLMPromptExecutor` with an `AnthropicLLMClient`.
 *
 * @param apiKey The API token used for authentication with the Anthropic LLM client.
 * @param httpClientFactory Factory used to create the underlying HTTP client.
 */
public fun simpleAnthropicExecutor(
    apiKey: String,
    httpClientFactory: KoogHttpClient.Factory,
): MultiLLMPromptExecutor = MultiLLMPromptExecutor(
    LLMProvider.Anthropic to AnthropicLLMClient(apiKey = apiKey, httpClientFactory = httpClientFactory)
)

/**
 * Creates an instance of `MultiLLMPromptExecutor` with an `OpenRouterLLMClient`.
 *
 * @param apiKey The API token used for authentication with the OpenRouter API.
 * @param httpClientFactory Factory used to create the underlying HTTP client.
 */
public fun simpleOpenRouterExecutor(
    apiKey: String,
    httpClientFactory: KoogHttpClient.Factory,
): MultiLLMPromptExecutor = MultiLLMPromptExecutor(
    LLMProvider.OpenRouter to OpenRouterLLMClient(apiKey = apiKey, httpClientFactory = httpClientFactory)
)

/**
 * Creates an instance of `MultiLLMPromptExecutor` with an `GoogleLLMClient`.
 *
 * @param apiKey The API token used for authentication with the Google AI service.
 * @param httpClientFactory Factory used to create the underlying HTTP client.
 */
public fun simpleGoogleAIExecutor(
    apiKey: String,
    httpClientFactory: KoogHttpClient.Factory,
): MultiLLMPromptExecutor = MultiLLMPromptExecutor(
    LLMProvider.Google to GoogleLLMClient(apiKey = apiKey, httpClientFactory = httpClientFactory)
)

/**
 * Creates an instance of `MultiLLMPromptExecutor` with an `OllamaClient`.
 *
 * @param baseUrl url used to access Ollama server.
 * @param httpClientFactory Factory used to create the underlying HTTP client.
 */
public fun simpleOllamaAIExecutor(
    baseUrl: String = "http://localhost:11434",
    httpClientFactory: KoogHttpClient.Factory,
): MultiLLMPromptExecutor = MultiLLMPromptExecutor(
    LLMProvider.Ollama to OllamaClient(baseUrl = baseUrl, httpClientFactory = httpClientFactory)
)

/**
 * Creates an instance of `MultiLLMPromptExecutor` with a `MistralAILLMClient`.
 *
 * @param apiKey The API token used for authentication with the Mistral AI provider.
 * @param httpClientFactory Factory used to create the underlying HTTP client.
 */
public fun simpleMistralAIExecutor(apiKey: String, httpClientFactory: KoogHttpClient.Factory): MultiLLMPromptExecutor =
    MultiLLMPromptExecutor(LLMProvider.MistralAI to MistralAILLMClient(apiKey = apiKey, httpClientFactory = httpClientFactory))
