package ai.koog.prompt.executor.llms.all

import ai.koog.http.client.DefaultHttpClientFactoryHolder
import ai.koog.prompt.executor.clients.openai.azure.AzureOpenAIServiceVersion
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor

/**
 * Convenience overload that resolves [ai.koog.http.client.KoogHttpClient.Factory] from
 * [DefaultHttpClientFactoryHolder]. JVM and Android only.
 *
 * @see simpleOpenAIExecutor
 */
public fun simpleOpenAIExecutor(apiToken: String): SingleLLMPromptExecutor =
    simpleOpenAIExecutor(apiToken, DefaultHttpClientFactoryHolder.getDefaultHttpClientFactory())

/**
 * Convenience overload that resolves [ai.koog.http.client.KoogHttpClient.Factory] from
 * [DefaultHttpClientFactoryHolder]. JVM and Android only.
 *
 * @see simpleAzureOpenAIExecutor
 */
public fun simpleAzureOpenAIExecutor(
    resourceName: String,
    deploymentName: String,
    version: AzureOpenAIServiceVersion,
    apiToken: String,
): SingleLLMPromptExecutor = simpleAzureOpenAIExecutor(
    resourceName = resourceName,
    deploymentName = deploymentName,
    version = version,
    apiToken = apiToken,
    httpClientFactory = DefaultHttpClientFactoryHolder.getDefaultHttpClientFactory(),
)

/**
 * Convenience overload that resolves [ai.koog.http.client.KoogHttpClient.Factory] from
 * [DefaultHttpClientFactoryHolder]. JVM and Android only.
 *
 * @see simpleAzureOpenAIExecutor
 */
public fun simpleAzureOpenAIExecutor(
    baseUrl: String,
    version: AzureOpenAIServiceVersion,
    apiToken: String,
): SingleLLMPromptExecutor = simpleAzureOpenAIExecutor(
    baseUrl = baseUrl,
    version = version,
    apiToken = apiToken,
    httpClientFactory = DefaultHttpClientFactoryHolder.getDefaultHttpClientFactory(),
)

/**
 * Convenience overload that resolves [ai.koog.http.client.KoogHttpClient.Factory] from
 * [DefaultHttpClientFactoryHolder]. JVM and Android only.
 *
 * @see simpleAnthropicExecutor
 */
public fun simpleAnthropicExecutor(apiKey: String): SingleLLMPromptExecutor =
    simpleAnthropicExecutor(apiKey, DefaultHttpClientFactoryHolder.getDefaultHttpClientFactory())

/**
 * Convenience overload that resolves [ai.koog.http.client.KoogHttpClient.Factory] from
 * [DefaultHttpClientFactoryHolder]. JVM and Android only.
 *
 * @see simpleOpenRouterExecutor
 */
public fun simpleOpenRouterExecutor(apiKey: String): SingleLLMPromptExecutor =
    simpleOpenRouterExecutor(apiKey, DefaultHttpClientFactoryHolder.getDefaultHttpClientFactory())

/**
 * Convenience overload that resolves [ai.koog.http.client.KoogHttpClient.Factory] from
 * [DefaultHttpClientFactoryHolder]. JVM and Android only.
 *
 * @see simpleGoogleAIExecutor
 */
public fun simpleGoogleAIExecutor(apiKey: String): SingleLLMPromptExecutor =
    simpleGoogleAIExecutor(apiKey, DefaultHttpClientFactoryHolder.getDefaultHttpClientFactory())

/**
 * Convenience overload that resolves [ai.koog.http.client.KoogHttpClient.Factory] from
 * [DefaultHttpClientFactoryHolder]. JVM and Android only.
 *
 * @see simpleOllamaAIExecutor
 */
public fun simpleOllamaAIExecutor(
    baseUrl: String = "http://localhost:11434",
): SingleLLMPromptExecutor = simpleOllamaAIExecutor(
    baseUrl = baseUrl,
    httpClientFactory = DefaultHttpClientFactoryHolder.getDefaultHttpClientFactory(),
)

/**
 * Convenience overload that resolves [ai.koog.http.client.KoogHttpClient.Factory] from
 * [DefaultHttpClientFactoryHolder]. JVM and Android only.
 *
 * @see simpleMistralAIExecutor
 */
public fun simpleMistralAIExecutor(apiKey: String): SingleLLMPromptExecutor =
    simpleMistralAIExecutor(apiKey, DefaultHttpClientFactoryHolder.getDefaultHttpClientFactory())
