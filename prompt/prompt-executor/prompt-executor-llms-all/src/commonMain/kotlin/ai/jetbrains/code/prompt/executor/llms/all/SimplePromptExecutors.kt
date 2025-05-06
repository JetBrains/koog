package ai.jetbrains.code.prompt.executor.llms.all

import ai.jetbrains.code.prompt.executor.clients.anthropic.AnthropicDirectLLMClient
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAIDirectLLMClient
import ai.jetbrains.code.prompt.executor.llms.SingleLLMCodePromptExecutor

fun simpleOpenAIExecutor(apiToken: String) = SingleLLMCodePromptExecutor(OpenAIDirectLLMClient(apiToken))

/**
 * Creates an instance of `SingleLLMCodePromptExecutor` with an `AnthropicDirectLLMClient`.
 *
 * @param apiToken The API token used for authentication with the Anthropic LLM client.
 */
fun simpleAnthropicExecutor(apiToken: String) = SingleLLMCodePromptExecutor(AnthropicDirectLLMClient(apiToken))