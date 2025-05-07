package ai.jetbrains.code.prompt.executor.llms.all

import ai.jetbrains.code.prompt.executor.clients.anthropic.AnthropicDirectLLMClient
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAIDirectLLMClient
import ai.jetbrains.code.prompt.executor.llms.SingleLLMPromptExecutor

fun simpleOpenAIExecutor(apiToken: String) = SingleLLMPromptExecutor(OpenAIDirectLLMClient(apiToken))

/**
 * Creates an instance of `SingleLLMPromptExecutor` with an `AnthropicDirectLLMClient`.
 *
 * @param apiToken The API token used for authentication with the Anthropic LLM client.
 */
fun simpleAnthropicExecutor(apiToken: String) = SingleLLMPromptExecutor(AnthropicDirectLLMClient(apiToken))