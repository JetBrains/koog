package ai.koog.spring.ai.chat

import ai.koog.prompt.llm.LLMProvider
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertInstanceOf
import org.junit.jupiter.api.assertThrows
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import reactor.core.publisher.Flux
import org.springframework.ai.chat.prompt.Prompt as SpringPrompt

class SpringAiChatModelProviderDetectorTest {

    @Test
    fun `should resolve all canonical Koog provider ids`() {
        val chatModel = mockk<ChatModel>(relaxed = true)
        val expected = mapOf(
            "google" to LLMProvider.Google,
            "openai" to LLMProvider.OpenAI,
            "anthropic" to LLMProvider.Anthropic,
            "meta" to LLMProvider.Meta,
            "alibaba" to LLMProvider.Alibaba,
            "openrouter" to LLMProvider.OpenRouter,
            "ollama" to LLMProvider.Ollama,
            "bedrock" to LLMProvider.Bedrock,
            "deepseek" to LLMProvider.DeepSeek,
            "mistralai" to LLMProvider.MistralAI,
            "oci" to LLMProvider.OCI,
            "minimax" to LLMProvider.MiniMax,
            "zhipuai" to LLMProvider.ZhipuAI,
            "huggingface" to LLMProvider.HuggingFace,
            "azure" to LLMProvider.Azure,
            "vertex" to LLMProvider.Vertex,
        )
        for ((id, provider) in expected) {
            assertSame(provider, SpringAiChatModelProviderDetector.detect(chatModel, id)) {
                "Failed for provider id '$id'"
            }
        }
    }

    @Test
    fun `should throw on unknown explicit provider id`() {
        val chatModel = mockk<ChatModel>(relaxed = true)
        val ex = assertThrows<IllegalArgumentException> {
            SpringAiChatModelProviderDetector.detect(chatModel, "nonexistent")
        }
        assert(ex.message!!.contains("nonexistent"))
    }

    @Test
    fun `should auto-detect provider when class name starts with provider id`() {
        val cases: List<Pair<ChatModel, LLMProvider>> = listOf(
            GoogleGenAiChatModel() to LLMProvider.Google,
            OpenAiChatModel() to LLMProvider.OpenAI,
            AnthropicChatModel() to LLMProvider.Anthropic,
            OllamaChatModel() to LLMProvider.Ollama,
            BedrockConverseChatModel() to LLMProvider.Bedrock,
            MistralAiChatModel() to LLMProvider.MistralAI,
            DeepSeekChatModel() to LLMProvider.DeepSeek,
            MiniMaxChatModel() to LLMProvider.MiniMax,
            ZhipuAiChatModel() to LLMProvider.ZhipuAI,
            HuggingFaceChatModel() to LLMProvider.HuggingFace,
            OCICohereChatModel() to LLMProvider.OCI,
            AzureOpenAiChatModel() to LLMProvider.Azure,
            VertexAiGeminiChatModel() to LLMProvider.Vertex,
        )
        for ((chatModel, expectedProvider) in cases) {
            assertSame(expectedProvider, SpringAiChatModelProviderDetector.detect(chatModel, null)) {
                "Failed auto-detection for ${chatModel.javaClass.simpleName}, expected ${expectedProvider.id}"
            }
        }
    }

    @Test
    fun `should fallback to SpringAiLLMProvider for unknown ChatModel`() {
        val chatModel = mockk<ChatModel>(relaxed = true)
        assertInstanceOf<SpringAiLLMProvider>(SpringAiChatModelProviderDetector.detect(chatModel, null))
    }

    // Base stub implementing ChatModel methods so that named subclasses can be instantiated directly
    // (avoiding mockk-generated subclass names that break class-name-based auto-detection).
    private abstract class StubChatModel : ChatModel {
        override fun call(prompt: SpringPrompt): ChatResponse = throw UnsupportedOperationException()
        override fun stream(prompt: SpringPrompt): Flux<ChatResponse> = throw UnsupportedOperationException()
    }

    // Stub classes simulating Spring AI class names.
    // Named so that their lowercase simple name starts with the corresponding LLMProvider id.
    private class GoogleGenAiChatModel : StubChatModel()
    private class OpenAiChatModel : StubChatModel()
    private class AzureOpenAiChatModel : StubChatModel()
    private class AnthropicChatModel : StubChatModel()
    private class OllamaChatModel : StubChatModel()
    private class BedrockConverseChatModel : StubChatModel()
    private class MistralAiChatModel : StubChatModel()
    private class DeepSeekChatModel : StubChatModel()
    private class MiniMaxChatModel : StubChatModel()
    private class ZhipuAiChatModel : StubChatModel()
    private class HuggingFaceChatModel : StubChatModel()
    private class OCICohereChatModel : StubChatModel()
    private class VertexAiGeminiChatModel : StubChatModel()
}
