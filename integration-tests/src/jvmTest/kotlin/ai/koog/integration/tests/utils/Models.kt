package ai.koog.integration.tests.utils

import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.bedrock.BedrockModels
import ai.koog.prompt.executor.clients.google.GoogleModels
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
            OpenAIModels.Chat.GPT5,
            OpenAIModels.Reasoning.O1,
            OpenAIModels.CostOptimized.GPT4_1Mini,
        )
    }

    @JvmStatic
    fun anthropicModels(): Stream<LLModel> {
        return Stream.of(
            AnthropicModels.Opus_4_1,
            AnthropicModels.Haiku_4_5,
            AnthropicModels.Sonnet_4_5,
        )
    }

    @JvmStatic
    fun googleModels(): Stream<LLModel> {
        return Stream.of(
            GoogleModels.Gemini2_5Pro,
            GoogleModels.Gemini2_5Flash,
        )
    }

    @JvmStatic
    fun bedrockModels(): Stream<LLModel> {
        return Stream.of(
            BedrockModels.MetaLlama3_1_70BInstruct,
            BedrockModels.AnthropicClaude4_5Sonnet,
        )
    }

    @JvmStatic
    fun bedrockEmbeddingModels(): Stream<LLModel> {
        return Stream.of(
            BedrockModels.Embeddings.AmazonTitanEmbedText
        )
    }

    @JvmStatic
    fun openRouterModels(): Stream<LLModel> = Stream.of(
        OpenRouterModels.DeepSeekV30324,
        OpenRouterModels.Qwen2_5,
    )

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
