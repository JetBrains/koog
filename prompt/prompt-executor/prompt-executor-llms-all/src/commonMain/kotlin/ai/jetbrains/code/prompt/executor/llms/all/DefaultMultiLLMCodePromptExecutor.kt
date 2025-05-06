package ai.jetbrains.code.prompt.executor.llms.all

import ai.jetbrains.code.prompt.executor.clients.anthropic.AnthropicDirectLLMClient
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAIDirectLLMClient
import ai.jetbrains.code.prompt.executor.llms.MultiLLMCodePromptExecutor
import ai.jetbrains.code.prompt.llm.LLMProvider

/**
 * Implementation of [MultiLLMCodePromptExecutor] that supports both OpenAI and Anthropic providers.
 *
 * @param openAIClient The OpenAI client
 * @param anthropicClient The Anthropic client
 */
class DefaultMultiLLMCodePromptExecutor(
    openAIClient: OpenAIDirectLLMClient,
    anthropicClient: AnthropicDirectLLMClient
) : MultiLLMCodePromptExecutor(
    LLMProvider.OpenAI to openAIClient,
    LLMProvider.Anthropic to anthropicClient
)