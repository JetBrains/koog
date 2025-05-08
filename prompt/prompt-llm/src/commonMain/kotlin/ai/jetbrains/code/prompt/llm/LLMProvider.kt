package ai.jetbrains.code.prompt.llm

import kotlinx.serialization.Serializable

@Serializable
sealed class LLMProvider(val id: String, val display: String) {
    @Serializable
    data object Google : LLMProvider("google", "Google")

    @Serializable
    data object OpenAI : LLMProvider("openai", "OpenAI")

    @Serializable
    data object Anthropic : LLMProvider("anthropic", "Anthropic")

    @Serializable
    data object Meta : LLMProvider("meta", "Meta")

    @Serializable
    data object Alibaba : LLMProvider("alibaba", "Alibaba")
    
    @Serializable
    data object OpenRouter : LLMProvider("openrouter", "OpenRouter")
}