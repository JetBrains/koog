package ai.jetbrains.code.prompt.llm

import kotlinx.serialization.Serializable

@Serializable
public sealed class LLMProvider(public val id: String, public val display: String) {
    @Serializable
    public data object Google : LLMProvider("google", "Google")

    @Serializable
    public data object OpenAI : LLMProvider("openai", "OpenAI")

    @Serializable
    public data object Anthropic : LLMProvider("anthropic", "Anthropic")

    @Serializable
    public data object Meta : LLMProvider("meta", "Meta")

    @Serializable
    public data object Alibaba : LLMProvider("alibaba", "Alibaba")
    
    @Serializable
    public data object OpenRouter : LLMProvider("openrouter", "OpenRouter")
}
