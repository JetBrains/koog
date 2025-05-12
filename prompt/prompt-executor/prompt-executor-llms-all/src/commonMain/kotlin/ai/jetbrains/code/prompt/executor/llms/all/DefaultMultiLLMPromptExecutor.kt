package ai.jetbrains.code.prompt.executor.llms.all

import ai.jetbrains.code.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAILLMClient
import ai.jetbrains.code.prompt.executor.llms.MultiLLMPromptExecutor
import ai.jetbrains.code.prompt.llm.LLMProvider

/**
 * Implementation of [MultiLLMPromptExecutor] that supports both OpenAI and Anthropic providers.
 *
 * @param openAIClient The OpenAI client
 * @param anthropicClient The Anthropic client
 */
class DefaultMultiLLMPromptExecutor(
    openAIClient: OpenAILLMClient,
    anthropicClient: AnthropicLLMClient
) : MultiLLMPromptExecutor(
    LLMProvider.OpenAI to openAIClient,
    LLMProvider.Anthropic to anthropicClient
)