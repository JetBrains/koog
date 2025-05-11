package ai.jetbrains.code.prompt.executor.llms.all

import ai.jetbrains.code.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAILLMClient
import ai.jetbrains.code.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.jetbrains.code.prompt.executor.llms.SingleLLMPromptExecutor

fun simpleOpenAIExecutor(apiToken: String) = SingleLLMPromptExecutor(OpenAILLMClient(apiToken))

/**
 * Creates an instance of `SingleLLMPromptExecutor` with an `AnthropicLLMClient`.
 *
 * @param apiToken The API token used for authentication with the Anthropic LLM client.
 */
fun simpleAnthropicExecutor(apiToken: String) = SingleLLMPromptExecutor(AnthropicLLMClient(apiToken))

/**
 * Creates an instance of `SingleLLMPromptExecutor` with an `OpenRouterLLMClient`.
 *
 * @param apiToken The API token used for authentication with the OpenRouter API.
 */
fun simpleOpenRouterExecutor(apiToken: String) = SingleLLMPromptExecutor(OpenRouterLLMClient(apiToken))