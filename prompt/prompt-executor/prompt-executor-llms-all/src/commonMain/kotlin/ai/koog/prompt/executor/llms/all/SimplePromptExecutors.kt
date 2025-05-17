package ai.koog.prompt.executor.llms.all

import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient

public fun simpleOpenAIExecutor(apiToken: String): SingleLLMPromptExecutor = SingleLLMPromptExecutor(OpenAILLMClient(apiToken))

/**
 * Creates an instance of `SingleLLMPromptExecutor` with an `AnthropicLLMClient`.
 *
 * @param apiToken The API token used for authentication with the Anthropic LLM client.
 */
public fun simpleAnthropicExecutor(apiToken: String): SingleLLMPromptExecutor = SingleLLMPromptExecutor(AnthropicLLMClient(apiToken))

/**
 * Creates an instance of `SingleLLMPromptExecutor` with an `OpenRouterLLMClient`.
 *
 * @param apiToken The API token used for authentication with the OpenRouter API.
 */
public fun simpleOpenRouterExecutor(apiToken: String): SingleLLMPromptExecutor = SingleLLMPromptExecutor(OpenRouterLLMClient(apiToken))

/**
 * Creates an instance of `SingleLLMPromptExecutor` with an `GoogleLLMClient`.
 *
 * @param apiToken The API token used for authentication with the Google AI service.
 */
public fun simpleGoogleAIExecutor(apiToken: String): SingleLLMPromptExecutor = SingleLLMPromptExecutor(GoogleLLMClient(apiToken))

/**
 * Creates an instance of `SingleLLMPromptExecutor` with an `OllamaClient`.
 *
 * @param apiToken The API token used for authentication with the Google AI service.
 */
public fun simpleOllamaAIExecutor(baseUrl: String = "http://localhost:11434"): SingleLLMPromptExecutor = SingleLLMPromptExecutor(OllamaClient(baseUrl))