package ai.jetbrains.code.integration.tests

import ai.jetbrains.code.prompt.executor.clients.anthropic.AnthropicModels
import ai.jetbrains.code.prompt.executor.clients.google.GoogleModels
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAIModels
import ai.jetbrains.code.prompt.llm.LLModel
import java.util.stream.Stream

object Models {
    @JvmStatic
    fun openAIModels(): Stream<LLModel> {
        return Stream.of(
            OpenAIModels.Chat.GPT4o,
            OpenAIModels.Chat.GPT4_1,

            OpenAIModels.Reasoning.GPT4oMini,
            OpenAIModels.Reasoning.O3Mini,
            OpenAIModels.Reasoning.O1Mini,
            OpenAIModels.Reasoning.O3,
            OpenAIModels.Reasoning.O1,

            OpenAIModels.CostOptimized.O4Mini,
            OpenAIModels.CostOptimized.GPT4_1Nano,
            OpenAIModels.CostOptimized.GPT4_1Mini,
        )
    }

    @JvmStatic
    fun anthropicModels(): Stream<LLModel> {
        return Stream.of(
            AnthropicModels.Opus,

            AnthropicModels.Haiku_3,
            AnthropicModels.Haiku_3_5,

            AnthropicModels.Sonnet_3,
            AnthropicModels.Sonnet_3_5,
            AnthropicModels.Sonnet_3_7,
        )
    }

    @JvmStatic
    fun googleModels(): Stream<LLModel> {
        return Stream.of(
            GoogleModels.Gemini1_5Pro,
            GoogleModels.Gemini1_5ProLatest,
            GoogleModels.Gemini1_5Pro001,
            GoogleModels.Gemini1_5Pro002,
            GoogleModels.Gemini2_5ProPreview0506,
            GoogleModels.GeminiProVision,

            GoogleModels.Gemini2_0Flash,
            GoogleModels.Gemini2_0Flash001,
            GoogleModels.Gemini2_0FlashLite,
            GoogleModels.Gemini2_0FlashLite001,
            GoogleModels.Gemini1_5Flash,
            GoogleModels.Gemini1_5FlashLatest,
            GoogleModels.Gemini1_5Flash001,
            GoogleModels.Gemini1_5Flash002,
            GoogleModels.Gemini1_5Flash8B,
            GoogleModels.Gemini1_5Flash8B001,
            GoogleModels.Gemini1_5Flash8BLatest,
            GoogleModels.Gemini2_5FlashPreview0417,
        )
    }
}