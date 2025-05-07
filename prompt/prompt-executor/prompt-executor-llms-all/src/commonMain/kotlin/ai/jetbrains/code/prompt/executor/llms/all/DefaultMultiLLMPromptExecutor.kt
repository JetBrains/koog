package ai.jetbrains.code.prompt.executor.llms.all

import ai.jetbrains.code.prompt.executor.clients.anthropic.AnthropicDirectLLMClient
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAIDirectLLMClient
import ai.jetbrains.code.prompt.executor.llms.MultiLLMPromptExecutor
import ai.jetbrains.code.prompt.llm.LLMProvider

/**
 * Implementation of [MultiLLMPromptExecutor] that supports both OpenAI and Anthropic providers.
 *
 * @param openAIClient The OpenAI client
 * @param anthropicClient The Anthropic client
 */
class DefaultMultiLLMPromptExecutor(
    openAIClient: OpenAIDirectLLMClient,
    anthropicClient: AnthropicDirectLLMClient
) : MultiLLMPromptExecutor(
    LLMProvider.OpenAI to openAIClient,
    LLMProvider.Anthropic to anthropicClient
)