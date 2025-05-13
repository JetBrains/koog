package ai.jetbrains.code.prompt.executor.llms.all

import ai.jetbrains.code.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.jetbrains.code.prompt.executor.clients.google.GoogleLLMClient
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAILLMClient
import ai.jetbrains.code.prompt.executor.llms.MultiLLMPromptExecutor
import ai.jetbrains.code.prompt.llm.LLMProvider

/**
 * Implementation of [MultiLLMPromptExecutor] that supports OpenAI, Anthropic, and Google providers.
 *
 * @param openAIClient The OpenAI client
 * @param anthropicClient The Anthropic client
 * @param googleClient The Google client
 */
public class DefaultMultiLLMPromptExecutor(
    openAIClient: OpenAILLMClient,
    anthropicClient: AnthropicLLMClient,
    googleClient: GoogleLLMClient,
) : MultiLLMPromptExecutor(
    LLMProvider.OpenAI to openAIClient,
    LLMProvider.Anthropic to anthropicClient,
    LLMProvider.Google to googleClient,
)
