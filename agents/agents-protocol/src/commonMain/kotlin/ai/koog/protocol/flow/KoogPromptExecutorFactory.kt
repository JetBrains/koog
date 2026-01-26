package ai.koog.protocol.flow

import ai.koog.agents.core.system.getEnvironmentVariableOrNull
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.mistralai.MistralAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.llms.getModelFromIdentifier
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Factory for creating PromptExecutor instances based on model configuration.
 */
public object KoogPromptExecutorFactory {

    private val logger = KotlinLogging.logger { }

    /**
     * Environment variable names for each provider.
     */
    private object DefinedAPIKeys {
        const val OPENAI_API_KEY = "OPENAI_API_KEY"
        const val ANTHROPIC_API_KEY = "ANTHROPIC_API_KEY"
        const val GOOGLE_API_KEY = "GOOGLE_API_KEY"
        const val MISTRAL_API_KEY = "MISTRAL_API_KEY"
        const val DEEPSEEK_API_KEY = "DEEPSEEK_API_KEY"
        const val OPENROUTER_API_KEY = "OPENROUTER_API_KEY"
        const val OLLAMA_BASE_URL = "OLLAMA_BASE_URL"
    }

    /**
     * Resolves model string to LLModel instance.
     *
     * Supports various model string formats:
     * - "provider/model-id" (e.g., "openai/gpt-4o")
     * - Just "model-id" (defaults to OpenAI)
     *
     * @param modelString Model string in format "provider/model-id" (e.g., "openai/gpt-4o")
     * @return LLModel instance
     */
    public fun resolveModel(modelString: String?, defaultModel: String?): LLModel {
        if (modelString == null) {
            return OpenAIModels.Chat.GPT4o
        }

        val (providerIdentifier, modelIdentifier) = modelString.split("/", limit = 2)

        val fullModelIdentifier =
            if (providerIdentifier.lowercase() == LLMProvider.OpenAI.id && !modelString.contains(".")) {
                // For OpenAI, we need to specify a category if not provided
                // Default to "chat" category if not specified
                "$providerIdentifier.chat.$modelIdentifier"
            } else {
                "$providerIdentifier.$modelIdentifier"
            }

        val normalizedModelIdentifier = fullModelIdentifier
            .replace("-", "")
            .replace("_", "")
            .lowercase()

        val model = getModelFromIdentifier(normalizedModelIdentifier)
            ?: getModelFromIdentifier(normalizedModelIdentifier)
            ?: error("Unable to find model identifier from string: $normalizedModelIdentifier")

        logger.debug { "Resolved input model config (model string: $modelString, default model: $defaultModel) to model: $model" }
        return model
    }

    /**
     * Builds a MultiLLMPromptExecutor from a list of LLModel instances.
     *
     * @param models List of LLModel instances that will be used by the executor
     * @return PromptExecutor configured with clients for all required providers
     *
     * @throws [IllegalStateException] if required, environment variables are not set for any provider
     */
    public fun buildFromModels(models: List<LLModel>): PromptExecutor? {
        if (models.isEmpty()) {
            logger.warn { "No models provided. PromptExecutor will not be created." }
            return null
        }

        logger.info { "Building PromptExecutor for ${models.size} model(s): ${models.map { it.id }}" }

        val providers = models.map { it.provider }.distinct()
        logger.debug { "Detected ${providers.size} unique provider(s): ${providers.map { provider -> provider.id }}" }

        val llmProvidersToClient = providers.mapNotNull { provider ->
            logger.debug { "Creating client for provider '${provider.display}' (${models.size} model(s))" }

            val client = createClientForProvider(provider) ?: return@mapNotNull null
            logger.info { "Successfully created ${provider.display} client" }

            provider to client
        }

        if (llmProvidersToClient.isEmpty()) {
            return null
        }

        val promptExecutor = MultiLLMPromptExecutor(*llmProvidersToClient.toTypedArray())
        logger.info { "Successfully created PromptExecutor with ${llmProvidersToClient.size} provider(s)" }

        return promptExecutor
    }

    /**
     * Creates an LLMClient for the specified provider by reading credentials from environment variables.
     *
     * @param provider The LLM provider to create a client for
     * @return LLMClient instance, or null if the required credentials are not available
     */
    private fun createClientForProvider(provider: LLMProvider): LLMClient? {
        return when (provider) {
            LLMProvider.OpenAI -> createOpenAIClient()
            LLMProvider.Anthropic -> createAnthropicClient()
            LLMProvider.Google -> createGoogleClient()
            LLMProvider.MistralAI -> createMistralClient()
            LLMProvider.DeepSeek -> createDeepSeekClient()
            LLMProvider.OpenRouter -> createOpenRouterClient()
            LLMProvider.Ollama -> createOllamaClient()
            LLMProvider.Bedrock -> {
                logger.warn { "Bedrock provider is not yet supported by PromptExecutorFactory" }
                null
            }
            else -> {
                logger.warn { "Unknown provider: ${provider.display}" }
                null
            }
        }
    }

    private fun createOpenAIClient(): LLMClient? {
        val apiKey = getEnvironmentVariableOrNull(DefinedAPIKeys.OPENAI_API_KEY) ?: run {
            logger.error { "Missing environment variable: ${DefinedAPIKeys.OPENAI_API_KEY}" }
            return null
        }
        return OpenAILLMClient(apiKey)
    }

    private fun createAnthropicClient(): LLMClient? {
        val apiKey = getEnvironmentVariableOrNull(DefinedAPIKeys.ANTHROPIC_API_KEY) ?: run {
            logger.error { "Missing environment variable: ${DefinedAPIKeys.ANTHROPIC_API_KEY}" }
            return null
        }
        return AnthropicLLMClient(apiKey)
    }

    private fun createGoogleClient(): LLMClient? {
        val apiKey = getEnvironmentVariableOrNull(DefinedAPIKeys.GOOGLE_API_KEY) ?: run {
            logger.error { "Missing environment variable: ${DefinedAPIKeys.GOOGLE_API_KEY}" }
            return null
        }
        return GoogleLLMClient(apiKey)
    }

    private fun createMistralClient(): LLMClient? {
        val apiKey = getEnvironmentVariableOrNull(DefinedAPIKeys.MISTRAL_API_KEY) ?: run {
            logger.error { "Missing environment variable: ${DefinedAPIKeys.MISTRAL_API_KEY}" }
            return null
        }
        return MistralAILLMClient(apiKey)
    }

    private fun createDeepSeekClient(): LLMClient? {
        val apiKey = getEnvironmentVariableOrNull(DefinedAPIKeys.DEEPSEEK_API_KEY) ?: run {
            logger.error { "Missing environment variable: ${DefinedAPIKeys.DEEPSEEK_API_KEY}" }
            return null
        }
        return DeepSeekLLMClient(apiKey)
    }

    private fun createOpenRouterClient(): LLMClient? {
        val apiKey = getEnvironmentVariableOrNull(DefinedAPIKeys.OPENROUTER_API_KEY) ?: run {
            logger.error { "Missing environment variable: ${DefinedAPIKeys.OPENROUTER_API_KEY}" }
            return null
        }
        return OpenRouterLLMClient(apiKey)
    }

    private fun createOllamaClient(): LLMClient {
        // Ollama doesn't require an API key but allows custom base URL
        val baseUrl = getEnvironmentVariableOrNull(DefinedAPIKeys.OLLAMA_BASE_URL) ?: "http://localhost:11434"
        logger.debug { "Using Ollama base URL: $baseUrl" }
        return OllamaClient(baseUrl)
    }
}
