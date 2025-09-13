package ai.koog.ktor

import ai.koog.ktor.utils.getModelFromIdentifier
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ModelIdentifierParsingTest {
    // OpenAI model identifier tests
    @Test
    fun testOpenAIChatModels() = runTest {
        // Test GPT-4o
        val gpt4o = getModelFromIdentifier("openai.chat.gpt4o")
        assertNotNull(gpt4o)
        assertEquals(LLMProvider.OpenAI, gpt4o.provider)
        assertEquals(OpenAIModels.Chat.GPT4o, gpt4o)

        // Test GPT-4.1
        val gpt4_1 = getModelFromIdentifier("openai.chat.gpt4_1")
        assertNotNull(gpt4_1)
        assertEquals(LLMProvider.OpenAI, gpt4_1.provider)
        assertEquals(OpenAIModels.Chat.GPT4_1, gpt4_1)
    }

    @Test
    fun testOpenAIReasoningModels() = runTest {
        // Test GPT-4o Mini
        val o4Mini = getModelFromIdentifier("openai.reasoning.o4mini")
        assertNotNull(o4Mini)
        assertEquals(LLMProvider.OpenAI, o4Mini.provider)
        assertEquals(OpenAIModels.Reasoning.O4Mini, o4Mini)

        // Test O3 Mini
        val o3Mini = getModelFromIdentifier("openai.reasoning.o3mini")
        assertNotNull(o3Mini)
        assertEquals(LLMProvider.OpenAI, o3Mini.provider)
        assertEquals(OpenAIModels.Reasoning.O3Mini, o3Mini)

        // Test O3
        val o3 = getModelFromIdentifier("openai.reasoning.o3")
        assertNotNull(o3)
        assertEquals(LLMProvider.OpenAI, o3.provider)
        assertEquals(OpenAIModels.Reasoning.O3, o3)

        // Test O1
        val o1 = getModelFromIdentifier("openai.reasoning.o1")
        assertNotNull(o1)
        assertEquals(LLMProvider.OpenAI, o1.provider)
        assertEquals(OpenAIModels.Reasoning.O1, o1)
    }

    @Test
    fun testOpenAICostOptimizedModels() = runTest {
        // Test O4 Mini
        val o4Mini = getModelFromIdentifier("openai.costoptimized.o4mini")
        assertNotNull(o4Mini)
        assertEquals(LLMProvider.OpenAI, o4Mini.provider)
        assertEquals(OpenAIModels.CostOptimized.O4Mini, o4Mini)

        // Test GPT-4.1 Nano
        val gpt4_1Nano = getModelFromIdentifier("openai.costoptimized.gpt4_1nano")
        assertNotNull(gpt4_1Nano)
        assertEquals(LLMProvider.OpenAI, gpt4_1Nano.provider)
        assertEquals(OpenAIModels.CostOptimized.GPT4_1Nano, gpt4_1Nano)

        // Test GPT-4.1 Mini
        val gpt4_1Mini = getModelFromIdentifier("openai.costoptimized.gpt4_1mini")
        assertNotNull(gpt4_1Mini)
        assertEquals(LLMProvider.OpenAI, gpt4_1Mini.provider)
        assertEquals(OpenAIModels.CostOptimized.GPT4_1Mini, gpt4_1Mini)
    }

    @Test
    fun testOpenAIAudioModels() = runTest {
        // Test GPT-4o Mini Audio
        val gpt4oMiniAudio = getModelFromIdentifier("openai.audio.gpt4ominiaudio")
        assertNotNull(gpt4oMiniAudio)
        assertEquals(LLMProvider.OpenAI, gpt4oMiniAudio.provider)
        assertEquals(OpenAIModels.Audio.GPT4oMiniAudio, gpt4oMiniAudio)

        // Test GPT-4o Audio
        val gpt4oAudio = getModelFromIdentifier("openai.audio.gpt4oaudio")
        assertNotNull(gpt4oAudio)
        assertEquals(LLMProvider.OpenAI, gpt4oAudio.provider)
        assertEquals(OpenAIModels.Audio.GPT4oAudio, gpt4oAudio)
    }

    @Test
    fun testOpenAIEmbeddingsModels() = runTest {
        // Test Text Embedding 3 Small
        val textEmbedding3Small = getModelFromIdentifier("openai.embeddings.textembedding3small")
        assertNotNull(textEmbedding3Small)
        assertEquals(LLMProvider.OpenAI, textEmbedding3Small.provider)
        assertEquals(OpenAIModels.Embeddings.TextEmbedding3Small, textEmbedding3Small)

        // Test Text Embedding 3 Large
        val textEmbedding3Large = getModelFromIdentifier("openai.embeddings.textembedding3large")
        assertNotNull(textEmbedding3Large)
        assertEquals(LLMProvider.OpenAI, textEmbedding3Large.provider)
        assertEquals(OpenAIModels.Embeddings.TextEmbedding3Large, textEmbedding3Large)

        // Test Text Embedding Ada 002
        val textEmbeddingAda002 = getModelFromIdentifier("openai.embeddings.textembeddingada002")
        assertNotNull(textEmbeddingAda002)
        assertEquals(LLMProvider.OpenAI, textEmbeddingAda002.provider)
        assertEquals(OpenAIModels.Embeddings.TextEmbeddingAda002, textEmbeddingAda002)
    }

    @Test
    fun testOpenAIModerationModels() = runTest {
        // Test Omni Moderation
        val omniModeration = getModelFromIdentifier("openai.moderation.omni")
        assertNotNull(omniModeration)
        assertEquals(LLMProvider.OpenAI, omniModeration.provider)
        assertEquals(OpenAIModels.Moderation.Omni, omniModeration)
    }

    // Anthropic model identifier tests
    @Test
    fun testAnthropicModels() = runTest {
        // Test Opus 3
        val opus3 = getModelFromIdentifier("anthropic.opus_3")
        assertNotNull(opus3)
        assertEquals(LLMProvider.Anthropic, opus3.provider)
        assertEquals(AnthropicModels.Opus_3, opus3)

        // Test Opus 4
        val opus4 = getModelFromIdentifier("anthropic.opus_4")
        assertNotNull(opus4)
        assertEquals(LLMProvider.Anthropic, opus4.provider)
        assertEquals(AnthropicModels.Opus_4, opus4)

        // Test Opus 4.1
        val opus4_1 = getModelFromIdentifier("anthropic.opus_4_1")
        assertNotNull(opus4_1)
        assertEquals(LLMProvider.Anthropic, opus4_1.provider)
        assertEquals(AnthropicModels.Opus_4_1, opus4_1)

        // Test Haiku 3
        val haiku3 = getModelFromIdentifier("anthropic.haiku_3")
        assertNotNull(haiku3)
        assertEquals(LLMProvider.Anthropic, haiku3.provider)
        assertEquals(AnthropicModels.Haiku_3, haiku3)

        // Test Haiku 3.5
        val haiku3_5 = getModelFromIdentifier("anthropic.haiku_3_5")
        assertNotNull(haiku3_5)
        assertEquals(LLMProvider.Anthropic, haiku3_5.provider)
        assertEquals(AnthropicModels.Haiku_3_5, haiku3_5)

        // Test Sonnet 3.5
        val sonnet3_5 = getModelFromIdentifier("anthropic.sonnet_3_5")
        assertNotNull(sonnet3_5)
        assertEquals(LLMProvider.Anthropic, sonnet3_5.provider)
        assertEquals(AnthropicModels.Sonnet_3_5, sonnet3_5)

        // Test Sonnet 3.7
        val sonnet3_7 = getModelFromIdentifier("anthropic.sonnet_3_7")
        assertNotNull(sonnet3_7)
        assertEquals(LLMProvider.Anthropic, sonnet3_7.provider)
        assertEquals(AnthropicModels.Sonnet_3_7, sonnet3_7)

        // Test Sonnet 4
        val sonnet4 = getModelFromIdentifier("anthropic.sonnet_4")
        assertNotNull(sonnet4)
        assertEquals(LLMProvider.Anthropic, sonnet4.provider)
        assertEquals(AnthropicModels.Sonnet_4, sonnet4)
    }

    // Google model identifier tests
    @Test
    fun testGoogleModels() = runTest {
        // Test Gemini 2.0 Flash
        val gemini20Flash = getModelFromIdentifier("google.gemini2_0flash")
        assertNotNull(gemini20Flash)
        assertEquals(LLMProvider.Google, gemini20Flash.provider)
        assertEquals(GoogleModels.Gemini2_0Flash, gemini20Flash)

        val gemini25Pro = getModelFromIdentifier("google.gemini2_5pro")
        assertNotNull(gemini25Pro)
        assertEquals(LLMProvider.Google, gemini25Pro.provider)
        assertEquals(GoogleModels.Gemini2_5Pro, gemini25Pro)

        val gemini25FlashLite = getModelFromIdentifier("google.gemini2_5flashlite")
        assertNotNull(gemini25FlashLite)
        assertEquals(LLMProvider.Google, gemini25FlashLite.provider)
        assertEquals(GoogleModels.Gemini2_5FlashLite, gemini25FlashLite)
    }

    // OpenRouter model identifier tests
    @Test
    fun testOpenRouterModels() = runTest {
        // Test Claude 3 Sonnet
        val claude3Sonnet = getModelFromIdentifier("openrouter.claude3sonnet")
        assertNotNull(claude3Sonnet)
        assertEquals(LLMProvider.OpenRouter, claude3Sonnet.provider)
        assertEquals(OpenRouterModels.Claude3Sonnet, claude3Sonnet)

        // Test Claude 3 Haiku
        val claude3Haiku = getModelFromIdentifier("openrouter.claude3haiku")
        assertNotNull(claude3Haiku)
        assertEquals(LLMProvider.OpenRouter, claude3Haiku.provider)
        assertEquals(OpenRouterModels.Claude3Haiku, claude3Haiku)

        // Test GPT-4
        val gpt4 = getModelFromIdentifier("openrouter.gpt4")
        assertNotNull(gpt4)
        assertEquals(LLMProvider.OpenRouter, gpt4.provider)
        assertEquals(OpenRouterModels.GPT4, gpt4)

        // Test GPT-4o
        val gpt4o = getModelFromIdentifier("openrouter.gpt4o")
        assertNotNull(gpt4o)
        assertEquals(LLMProvider.OpenRouter, gpt4o.provider)
        assertEquals(OpenRouterModels.GPT4o, gpt4o)

        // Test GPT-4 Turbo
        val gpt4Turbo = getModelFromIdentifier("openrouter.gpt4turbo")
        assertNotNull(gpt4Turbo)
        assertEquals(LLMProvider.OpenRouter, gpt4Turbo.provider)
        assertEquals(OpenRouterModels.GPT4Turbo, gpt4Turbo)

        // Test GPT-3.5 Turbo
        val gpt35Turbo = getModelFromIdentifier("openrouter.gpt35turbo")
        assertNotNull(gpt35Turbo)
        assertEquals(LLMProvider.OpenRouter, gpt35Turbo.provider)
        assertEquals(OpenRouterModels.GPT35Turbo, gpt35Turbo)
    }

    // DeepSeek model identifier tests
    @Test
    fun testDeepSeekModels() = runTest {
        // Test DeepSeek Chat
        val deepSeekChat = getModelFromIdentifier("deepseek.deepseek-chat")
        assertNotNull(deepSeekChat)
        assertEquals(LLMProvider.DeepSeek, deepSeekChat.provider)
        assertEquals(DeepSeekModels.DeepSeekChat, deepSeekChat)

        // Test DeepSeek Reasoner
        val deepSeekReasoner = getModelFromIdentifier("deepseek.deepseek-reasoner")
        assertNotNull(deepSeekReasoner)
        assertEquals(LLMProvider.DeepSeek, deepSeekReasoner.provider)
        assertEquals(DeepSeekModels.DeepSeekReasoner, deepSeekReasoner)
    }

    // Ollama model identifier tests
    @Test
    fun testOllamaGroqModels() = runTest {
        // Test with maker.model format
        val llama3GrokToolUse8B = getModelFromIdentifier("ollama.groq.llama3-grok-tool-use:8b")
        assertNotNull(llama3GrokToolUse8B)
        assertEquals(LLMProvider.Ollama, llama3GrokToolUse8B.provider)
        assertEquals(OllamaModels.Groq.LLAMA_3_GROK_TOOL_USE_8B, llama3GrokToolUse8B)

        // Test with model format
        val llama3GrokToolUse8BShort = getModelFromIdentifier("ollama.llama3-grok-tool-use:8b")
        assertNotNull(llama3GrokToolUse8BShort)
        assertEquals(LLMProvider.Ollama, llama3GrokToolUse8BShort.provider)
        assertEquals(OllamaModels.Groq.LLAMA_3_GROK_TOOL_USE_8B, llama3GrokToolUse8BShort)

        // Test with maker.model format
        val llama3GrokToolUse70B = getModelFromIdentifier("ollama.groq.llama3-grok-tool-use:70b")
        assertNotNull(llama3GrokToolUse70B)
        assertEquals(LLMProvider.Ollama, llama3GrokToolUse70B.provider)
        assertEquals(OllamaModels.Groq.LLAMA_3_GROK_TOOL_USE_70B, llama3GrokToolUse70B)
    }

    @Test
    fun testOllamaMetaModels() = runTest {
        // Test with maker.model format
        val llama3_2_3B = getModelFromIdentifier("ollama.meta.llama3.2:3b")
        assertNotNull(llama3_2_3B)
        assertEquals(LLMProvider.Ollama, llama3_2_3B.provider)
        assertEquals(OllamaModels.Meta.LLAMA_3_2_3B, llama3_2_3B)

        // Test with model format - using direct lookup for models with dots in the name
        val metaModel = OllamaModels.Meta.LLAMA_3_2_3B
        assertNotNull(metaModel)
        assertEquals(LLMProvider.Ollama, metaModel.provider)

        // Test with maker.model format
        val llama3_2 = getModelFromIdentifier("ollama.meta.llama3.2")
        assertNotNull(llama3_2)
        assertEquals(LLMProvider.Ollama, llama3_2.provider)
        assertEquals(OllamaModels.Meta.LLAMA_3_2, llama3_2)

        // Test with maker.model format
        val llama4 = getModelFromIdentifier("ollama.meta.llama4:latest")
        assertNotNull(llama4)
        assertEquals(LLMProvider.Ollama, llama4.provider)
        assertEquals(OllamaModels.Meta.LLAMA_4, llama4)

        // Test with maker.model format
        val llamaGuard3 = getModelFromIdentifier("ollama.meta.llama-guard3:latest")
        assertNotNull(llamaGuard3)
        assertEquals(LLMProvider.Ollama, llamaGuard3.provider)
        assertEquals(OllamaModels.Meta.LLAMA_GUARD_3, llamaGuard3)
    }

    @Test
    fun testOllamaAlibabaModels() = runTest {
        // Test with maker.model format
        val qwen2_5_05B = getModelFromIdentifier("ollama.alibaba.qwen2.5:0.5b")
        assertNotNull(qwen2_5_05B)
        assertEquals(LLMProvider.Ollama, qwen2_5_05B.provider)
        assertEquals(OllamaModels.Alibaba.QWEN_2_5_05B, qwen2_5_05B)

        // Test with model format - using direct lookup for models with dots in the name
        val alibabaModel = OllamaModels.Alibaba.QWEN_2_5_05B
        assertNotNull(alibabaModel)
        assertEquals(LLMProvider.Ollama, alibabaModel.provider)

        // Test with maker.model format
        val qwen3_06B = getModelFromIdentifier("ollama.alibaba.qwen3:0.6b")
        assertNotNull(qwen3_06B)
        assertEquals(LLMProvider.Ollama, qwen3_06B.provider)
        assertEquals(OllamaModels.Alibaba.QWEN_3_06B, qwen3_06B)

        // Test with maker.model format
        val qwq32B = getModelFromIdentifier("ollama.alibaba.qwq:32b")
        assertNotNull(qwq32B)
        assertEquals(LLMProvider.Ollama, qwq32B.provider)
        assertEquals(OllamaModels.Alibaba.QWQ_32B, qwq32B)

        // Test with maker.model format
        val qwq = getModelFromIdentifier("ollama.alibaba.qwq")
        assertNotNull(qwq)
        assertEquals(LLMProvider.Ollama, qwq.provider)
        assertEquals(OllamaModels.Alibaba.QWQ, qwq)

        // Test with maker.model format
        val qwenCoder2_5_32B = getModelFromIdentifier("ollama.alibaba.qwen2.5-coder:32b")
        assertNotNull(qwenCoder2_5_32B)
        assertEquals(LLMProvider.Ollama, qwenCoder2_5_32B.provider)
        assertEquals(OllamaModels.Alibaba.QWEN_CODER_2_5_32B, qwenCoder2_5_32B)
    }

    // Invalid model identifier tests
    @Test
    fun testInvalidModelIdentifiers() = runTest {
        // Test empty identifier
        val emptyIdentifier = getModelFromIdentifier("")
        assertNull(emptyIdentifier)

        // Test invalid provider
        val invalidProvider = getModelFromIdentifier("invalid.model")
        assertNull(invalidProvider)

        // Test invalid OpenAI category
        val invalidOpenAICategory = getModelFromIdentifier("openai.invalid.model")
        assertNull(invalidOpenAICategory)

        // Test invalid OpenAI model
        val invalidOpenAIModel = getModelFromIdentifier("openai.chat.invalid")
        assertNull(invalidOpenAIModel)

        // Test invalid Anthropic model
        val invalidAnthropicModel = getModelFromIdentifier("anthropic.invalid")
        assertNull(invalidAnthropicModel)

        // Test invalid Google model
        val invalidGoogleModel = getModelFromIdentifier("google.invalid")
        assertNull(invalidGoogleModel)

        // Test invalid OpenRouter model
        val invalidOpenRouterModel = getModelFromIdentifier("openrouter.invalid")
        assertNull(invalidOpenRouterModel)

        // Test invalid Ollama maker
        val invalidOllamaMaker = getModelFromIdentifier("ollama.invalid.model")
        assertNull(invalidOllamaMaker)

        // Test invalid Ollama model
        val invalidOllamaModel = getModelFromIdentifier("ollama.invalid")
        assertNull(invalidOllamaModel)
    }
}
