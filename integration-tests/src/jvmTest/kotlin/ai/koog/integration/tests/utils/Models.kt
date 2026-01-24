package ai.koog.integration.tests.utils

import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.bedrock.BedrockModels
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.mistralai.MistralAIModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.params.provider.Arguments
import java.util.stream.Stream

object Models {
    @JvmStatic
    fun openAIModels(): Stream<LLModel> {
        return Stream.of(
            OpenAIModels.Chat.GPT5_2, // reasoning
            OpenAIModels.Chat.GPT4_1, // non-reasoning
            OpenAIModels.Chat.GPT5_1Codex
        )
    }

    @JvmStatic
    fun anthropicModels(): Stream<LLModel> {
        return Stream.of(
            AnthropicModels.Opus_4_5,
            AnthropicModels.Haiku_4_5,
            AnthropicModels.Sonnet_4_5,
        )
    }

    @JvmStatic
    fun googleModels(): Stream<LLModel> {
        return Stream.of(
            GoogleModels.Gemini3_Pro_Preview,
            GoogleModels.Gemini2_5Pro,
            GoogleModels.Gemini2_5Flash,
        )
    }

    @JvmStatic
    fun openRouterModels(): Stream<LLModel> = Stream.of(
        OpenRouterModels.DeepSeekV30324,
        OpenRouterModels.Qwen2_5,
    )

    @JvmStatic
    fun mistralModels(): Stream<LLModel> = Stream.of(
        MistralAIModels.Chat.MistralMedium31,
    )

    @JvmStatic
    fun bedrockModels(): Stream<LLModel> {
        return Stream.of(
            BedrockModels.MetaLlama3_1_70BInstruct,
            BedrockModels.AnthropicClaude4_5Sonnet,
        )
    }

    @JvmStatic
    fun embeddingModels(): Stream<LLModel> {
        return Stream.of(
            BedrockModels.Embeddings.AmazonTitanEmbedText,
            OpenAIModels.Embeddings.TextEmbedding3Large,
            MistralAIModels.Embeddings.MistralEmbed,
            GoogleModels.Embeddings.GeminiEmbedding001,
        )
    }

    /**
     * Returns models that support content moderation capabilities.
     *
     * Note: For Bedrock, the model returned here is not actually used by the moderation API.
     * AWS Bedrock Guardrails are model-independent and configured at the client level.
     * However, we need to provide a Bedrock model here so that the integration tests can
     * instantiate a [ai.koog.prompt.executor.clients.bedrock.BedrockLLMClient] with the appropriate provider and guardrail settings.
     * The actual moderation behavior is determined by the guardrail configuration
     * in [getLLMClientForProvider], not by the model's capabilities.
     */
    @JvmStatic
    fun moderationModels(): Stream<LLModel> {
        return Stream.of(
            OpenAIModels.Moderation.Omni,
            MistralAIModels.Moderation.MistralModeration,
            BedrockModels.AnthropicClaude4_5Haiku
        )
    }

    @JvmStatic
    fun allCompletionModels(): Stream<LLModel> {
        return Stream.of(
            openAIModels(),
            anthropicModels(),
            googleModels(),
            openRouterModels(),
            bedrockModels(),
            mistralModels(),
        ).flatMap { it }
    }

    @JvmStatic
    fun reasoningCapableModels(): Stream<LLModel> {
        return Stream.of(
            // Replaced 5.2 with 5.1-Codex because of the unstable 5.2 behaviour, see KG-625
            OpenAIModels.Chat.GPT5_1Codex,
            AnthropicModels.Haiku_4_5,
            GoogleModels.Gemini2_5Pro,
            GoogleModels.Gemini3_Pro_Preview,
        )
    }

    @JvmStatic
    fun modelsWithVisionCapability(): Stream<Arguments> {
        return Stream.concat(
            openAIModels()
                .filter { model ->
                    model.capabilities.contains(LLMCapability.Vision.Image)
                }
                .map { model -> Arguments.of(model, getLLMClientForProvider(model.provider)) },

            anthropicModels()
                .filter { model ->
                    model.capabilities.contains(LLMCapability.Vision.Image)
                }
                .map { model -> Arguments.of(model, getLLMClientForProvider(model.provider)) },
        )
    }

    /**
     * Checks if a model's provider should be skipped based on the system property "skip.llm.providers".
     * This property is meant to be provided when running the tests
     * to signal one does not have an API key for this or that provider
     *
     * @param provider The LLM provider to check
     */
    @JvmStatic
    fun assumeAvailable(provider: LLMProvider) {
        val skipProvidersRaw = System.getProperty("skip.llm.providers", "")
        val skipProviders = skipProvidersRaw
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }

        val shouldSkip = skipProviders.contains(provider.id.lowercase())
        assumeTrue(
            !shouldSkip,
            "Test skipped because provider ${provider.display} is in the skip list ($skipProvidersRaw)"
        )
    }
}
