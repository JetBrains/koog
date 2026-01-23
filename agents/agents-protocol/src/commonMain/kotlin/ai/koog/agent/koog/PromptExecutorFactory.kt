package ai.koog.agent.koog

import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.utils.system.systemSecretsReader

private const val OPENAI_API_KEY_ENV = "OPENAI_API_KEY"

/**
 * Factory for creating PromptExecutor instances based on model configuration.
 */
public object PromptExecutorFactory {

    /**
     * Creates a PromptExecutor based on the model string (e.g., "openai/gpt-4o").
     * Currently, supports OpenAI models via environment variable.
     *
     * @param modelString Model string in format "provider/model-id" (e.g., "openai/gpt-4o")
     * @return PromptExecutor instance for the specified provider
     */
    public fun createExecutor(modelString: String?): PromptExecutor {
        val provider = modelString?.split("/", limit = 2)?.getOrNull(0)?.lowercase() ?: "openai"

        return when (provider) {
            "openai" -> {
                val apiKey = systemSecretsReader().getSecret(OPENAI_API_KEY_ENV)
                    ?: error("$OPENAI_API_KEY_ENV environment variable is not set")

                simpleOpenAIExecutor(apiKey)
            }
            else -> error("Unsupported provider: $provider. Currently only 'openai' is supported.")
        }
    }

    /**
     * Resolves model string to LLModel instance.
     *
     * @param modelString Model string in format "provider/model-id" (e.g., "openai/gpt-4o")
     * @return LLModel instance
     */
    public fun resolveModel(modelString: String?): LLModel {
        if (modelString == null) {
            return OpenAIModels.Chat.GPT4o
        }

        val parts = modelString.split("/", limit = 2)
        val provider = parts.getOrNull(0) ?: "openai"
        val modelId = parts.getOrNull(1) ?: modelString

        return when (provider.lowercase()) {
            "openai" -> resolveOpenAIModel(modelId)
            else -> LLModel(
                provider = resolveProvider(provider),
                id = modelId,
                capabilities = listOf(
                    LLMCapability.Temperature,
                    LLMCapability.Tools,
                    LLMCapability.ToolChoice,
                    LLMCapability.Completion
                ),
                contextLength = 128_000,
                maxOutputTokens = 16_384
            )
        }
    }

    private fun resolveOpenAIModel(modelId: String): LLModel {
        return when (modelId.lowercase()) {
            "gpt-4o" -> OpenAIModels.Chat.GPT4o
            "gpt-4o-mini" -> OpenAIModels.Chat.GPT4oMini
            "gpt-4.1" -> OpenAIModels.Chat.GPT4_1
            "gpt-4.1-mini" -> OpenAIModels.Chat.GPT4_1Mini
            "o1" -> OpenAIModels.Chat.O1
            "o3" -> OpenAIModels.Chat.O3
            "o3-mini" -> OpenAIModels.Chat.O3Mini
            else -> OpenAIModels.Chat.GPT4o
        }
    }

    private fun resolveProvider(provider: String): LLMProvider {
        return when (provider.lowercase()) {
            "openai" -> LLMProvider.OpenAI
            "anthropic" -> LLMProvider.Anthropic
            "google" -> LLMProvider.Google
            "meta" -> LLMProvider.Meta
            "alibaba" -> LLMProvider.Alibaba
            "openrouter" -> LLMProvider.OpenRouter
            "ollama" -> LLMProvider.Ollama
            "bedrock" -> LLMProvider.Bedrock
            "deepseek" -> LLMProvider.DeepSeek
            "mistralai" -> LLMProvider.MistralAI
            else -> LLMProvider.OpenAI
        }
    }
}
