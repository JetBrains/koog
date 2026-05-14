@file:JvmMultifileClass
@file:JvmName("SimplePromptExecutors")

package ai.koog.prompt.executor.llms.all

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
 * Convenience overload that constructs the underlying client via its JVM no-factory entry point
 * (resolves the default [ai.koog.http.client.KoogHttpClient.Factory] at call time). JVM and Android only.
 *
 * @see simpleOpenAIExecutor
 */
public fun simpleOpenAIExecutor(apiToken: String): SingleLLMPromptExecutor =
    SingleLLMPromptExecutor(OpenAILLMClient(apiToken))

/**
 * Convenience overload that constructs the underlying client via its JVM no-factory entry point
 * (resolves the default [ai.koog.http.client.KoogHttpClient.Factory] at call time). JVM and Android only.
 *
 * @see simpleAzureOpenAIExecutor
 */
public fun simpleAzureOpenAIExecutor(
    resourceName: String,
    deploymentName: String,
    version: AzureOpenAIServiceVersion,
    apiToken: String,
): SingleLLMPromptExecutor = SingleLLMPromptExecutor(
    OpenAILLMClient(
        apiKey = apiToken,
        settings = AzureOpenAIClientSettings(resourceName, deploymentName, version),
    )
)

/**
 * Convenience overload that constructs the underlying client via its JVM no-factory entry point
 * (resolves the default [ai.koog.http.client.KoogHttpClient.Factory] at call time). JVM and Android only.
 *
 * @see simpleAzureOpenAIExecutor
 */
public fun simpleAzureOpenAIExecutor(
    baseUrl: String,
    version: AzureOpenAIServiceVersion,
    apiToken: String,
): SingleLLMPromptExecutor = SingleLLMPromptExecutor(
    OpenAILLMClient(
        apiKey = apiToken,
        settings = AzureOpenAIClientSettings(baseUrl, version),
    )
)

/**
 * Convenience overload that constructs the underlying client via its JVM no-factory entry point
 * (resolves the default [ai.koog.http.client.KoogHttpClient.Factory] at call time). JVM and Android only.
 *
 * @see simpleAnthropicExecutor
 */
public fun simpleAnthropicExecutor(apiKey: String): SingleLLMPromptExecutor =
    SingleLLMPromptExecutor(AnthropicLLMClient(apiKey))

/**
 * Convenience overload that constructs the underlying client via its JVM no-factory entry point
 * (resolves the default [ai.koog.http.client.KoogHttpClient.Factory] at call time). JVM and Android only.
 *
 * @see simpleOpenRouterExecutor
 */
public fun simpleOpenRouterExecutor(apiKey: String): SingleLLMPromptExecutor =
    SingleLLMPromptExecutor(OpenRouterLLMClient(apiKey))

/**
 * Convenience overload that constructs the underlying client via its JVM no-factory entry point
 * (resolves the default [ai.koog.http.client.KoogHttpClient.Factory] at call time). JVM and Android only.
 *
 * @see simpleGoogleAIExecutor
 */
public fun simpleGoogleAIExecutor(apiKey: String): SingleLLMPromptExecutor =
    SingleLLMPromptExecutor(GoogleLLMClient(apiKey))

/**
 * Convenience overload that constructs the underlying client via its JVM no-factory entry point
 * (resolves the default [ai.koog.http.client.KoogHttpClient.Factory] at call time). JVM and Android only.
 *
 * @see simpleOllamaAIExecutor
 */
public fun simpleOllamaAIExecutor(
    baseUrl: String = "http://localhost:11434",
): SingleLLMPromptExecutor = SingleLLMPromptExecutor(OllamaClient(baseUrl = baseUrl))

/**
 * Convenience overload that constructs the underlying client via its JVM no-factory entry point
 * (resolves the default [ai.koog.http.client.KoogHttpClient.Factory] at call time). JVM and Android only.
 *
 * @see simpleMistralAIExecutor
 */
public fun simpleMistralAIExecutor(apiKey: String): SingleLLMPromptExecutor =
    SingleLLMPromptExecutor(MistralAILLMClient(apiKey))
