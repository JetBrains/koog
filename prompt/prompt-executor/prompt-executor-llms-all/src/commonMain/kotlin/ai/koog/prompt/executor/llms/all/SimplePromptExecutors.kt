package ai.koog.prompt.executor.llms.all

import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.bedrock.BedrockClientSettings
import ai.koog.prompt.executor.clients.bedrock.createBedrockLLMClient
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient

/**
 * Creates a `SingleLLMPromptExecutor` instance configured to use the OpenAI client.
 *
 * This method simplifies the setup process by creating an `OpenAILLMClient` with the provided API token
 * and wrapping it in a `SingleLLMPromptExecutor` to allow prompt execution with the OpenAI service.
 *
 * @param apiToken The API token used for authentication with the OpenAI API.
 * @return A new instance of `SingleLLMPromptExecutor` configured with the `OpenAILLMClient`.
 */
public fun simpleOpenAIExecutor(apiToken: String): SingleLLMPromptExecutor = SingleLLMPromptExecutor(OpenAILLMClient(apiToken))

/**
 * Creates an instance of `SingleLLMPromptExecutor` with an `AnthropicLLMClient`.
 *
 * @param apiKey The API token used for authentication with the Anthropic LLM client.
 */
public fun simpleAnthropicExecutor(apiKey: String): SingleLLMPromptExecutor = SingleLLMPromptExecutor(AnthropicLLMClient(apiKey))

/**
 * Creates an instance of `SingleLLMPromptExecutor` with an `OpenRouterLLMClient`.
 *
 * @param apiKey The API token used for authentication with the OpenRouter API.
 */
public fun simpleOpenRouterExecutor(apiKey: String): SingleLLMPromptExecutor = SingleLLMPromptExecutor(OpenRouterLLMClient(apiKey))

/**
 * Creates an instance of `SingleLLMPromptExecutor` with an `GoogleLLMClient`.
 *
 * @param apiKey The API token used for authentication with the Google AI service.
 */
public fun simpleGoogleAIExecutor(apiKey: String): SingleLLMPromptExecutor = SingleLLMPromptExecutor(GoogleLLMClient(apiKey))

/**
 * Creates an instance of `SingleLLMPromptExecutor` with an `OllamaClient`.
 *
 * @param baseUrl url used to access Ollama server.
 */
public fun simpleOllamaAIExecutor(baseUrl: String = "http://localhost:11434"): SingleLLMPromptExecutor = SingleLLMPromptExecutor(OllamaClient(baseUrl))

/**
 * Creates an instance of `SingleLLMPromptExecutor` with a `BedrockLLMClient`.
 *
 * @param awsAccessKeyId Your AWS Access Key ID.
 * @param awsSecretAccessKey Your AWS Secret Access Key.
 * @param settings Custom client settings for region and timeouts.
 */
public fun simpleBedrockExecutor(
    awsAccessKeyId: String,
    awsSecretAccessKey: String,
    settings: BedrockClientSettings = BedrockClientSettings()
): SingleLLMPromptExecutor =
    SingleLLMPromptExecutor(createBedrockLLMClient(awsAccessKeyId, awsSecretAccessKey, settings))
