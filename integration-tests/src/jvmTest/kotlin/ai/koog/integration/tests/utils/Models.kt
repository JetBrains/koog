package ai.koog.integration.tests.utils

import ai.koog.integration.tests.utils.TestUtils.readTestAnthropicKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readTestOpenAIKeyFromEnv
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.bedrock.BedrockModels
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
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
            OpenAIModels.Chat.GPT4o,
            OpenAIModels.Chat.GPT4_1,
            OpenAIModels.Chat.GPT5,
            OpenAIModels.Chat.GPT5Mini,
            OpenAIModels.Chat.GPT5Nano,

            OpenAIModels.Reasoning.O4Mini,
            OpenAIModels.Reasoning.O3Mini,
            OpenAIModels.Reasoning.O3,
            OpenAIModels.Reasoning.O1,

            OpenAIModels.CostOptimized.GPT4_1Nano,
            OpenAIModels.CostOptimized.GPT4_1Mini,
            OpenAIModels.CostOptimized.GPT4oMini,
        )
    }

    @JvmStatic
    fun anthropicModels(): Stream<LLModel> {
        return Stream.of(
            AnthropicModels.Opus_3,
            AnthropicModels.Opus_4,
            AnthropicModels.Opus_4_1,

            AnthropicModels.Haiku_3,
            AnthropicModels.Haiku_3_5,

            AnthropicModels.Sonnet_3_5,
            AnthropicModels.Sonnet_3_7,
            AnthropicModels.Sonnet_4,
        )
    }

    @JvmStatic
    fun googleModels(): Stream<LLModel> {
        return Stream.of(
            GoogleModels.Gemini2_5Pro,
            GoogleModels.Gemini2_0Flash,
            GoogleModels.Gemini2_0Flash001,
            GoogleModels.Gemini2_0FlashLite,
            GoogleModels.Gemini2_0FlashLite001,
            GoogleModels.Gemini2_5Flash,
        )
    }

    // listing not all profiles but one from each LLM provider
    @JvmStatic
    fun bedrockModels(): Stream<LLModel> {
        return Stream.of(
            BedrockModels.AnthropicClaude35Haiku,
            BedrockModels.MetaLlama3_1_70BInstruct,
        )
    }

    @JvmStatic
    fun openRouterModels(): Stream<LLModel> = Stream.of(
        OpenRouterModels.GPT5Nano,
        OpenRouterModels.DeepSeekV30324,
        OpenRouterModels.Claude4Sonnet,
        // ToDo add Gemini when KG-203 is fixed
        // OpenRouterModels.Gemini2_5FlashLite,
    )

    @JvmStatic
    fun modelsWithVisionCapability(): Stream<Arguments> {
        val openAIClient = OpenAILLMClient(readTestOpenAIKeyFromEnv())
        val anthropicClient = AnthropicLLMClient(readTestAnthropicKeyFromEnv())

        return Stream.concat(
            openAIModels()
                .filter { model ->
                    model.capabilities.contains(LLMCapability.Vision.Image)
                }
                .map { model -> Arguments.of(model, openAIClient) },

            anthropicModels()
                .filter { model ->
                    model.capabilities.contains(LLMCapability.Vision.Image)
                }
                .map { model -> Arguments.of(model, anthropicClient) }
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
