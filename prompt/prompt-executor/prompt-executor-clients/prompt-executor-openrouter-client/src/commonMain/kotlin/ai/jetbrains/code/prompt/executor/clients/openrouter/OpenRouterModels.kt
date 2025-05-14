package ai.jetbrains.code.prompt.executor.clients.openrouter

import ai.jetbrains.code.prompt.llm.LLMCapability
import ai.jetbrains.code.prompt.llm.LLMProvider
import ai.jetbrains.code.prompt.llm.LLModel

/**
 * OpenRouter models
 * Models available through the OpenRouter API
 */
public object OpenRouterModels {
    private val standardCapabilities: List<LLMCapability> = listOf(
        LLMCapability.Temperature,
        LLMCapability.Schema.JSON.Full,
        LLMCapability.Speculation,
        LLMCapability.Tools,
        LLMCapability.Completion
    )

    /**
     * Free model for testing and development
     */
    public val Phi4Reasoning: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "microsoft/phi-4-reasoning:free",
        capabilities = standardCapabilities
    )

    // Multimodal capabilities (including vision)
    private val multimodalCapabilities: List<LLMCapability> = standardCapabilities + LLMCapability.Vision
    
    // Anthropic models
    public val Claude3Opus: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "anthropic/claude-3-opus",
        capabilities = multimodalCapabilities
    )
    
    public val Claude3Sonnet: LLModel = LLModel(
        provider = LLMProvider.OpenRouter, 
        id = "anthropic/claude-3-sonnet", 
        capabilities = multimodalCapabilities
    )

    public val Claude3Haiku: LLModel = LLModel(
        provider = LLMProvider.OpenRouter, 
        id = "anthropic/claude-3-haiku", 
        capabilities = multimodalCapabilities
    )
    
    // OpenAI models
    public val GPT4: LLModel = LLModel(
        provider = LLMProvider.OpenRouter, 
        id = "openai/gpt-4", 
        capabilities = standardCapabilities
    )
    
    public val GPT4o: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "openai/gpt-4o",
        capabilities = multimodalCapabilities
    )
    
    public val GPT4Turbo: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "openai/gpt-4-turbo",
        capabilities = multimodalCapabilities
    )
    
    public val GPT35Turbo: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "openai/gpt-3.5-turbo",
        capabilities = standardCapabilities
    )
    
    // Google models
    public val Gemini14Pro: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "google/gemini-1.5-pro",
        capabilities = multimodalCapabilities
    )
    
    public val Gemini15Flash: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "google/gemini-1.5-flash",
        capabilities = multimodalCapabilities
    )
    
    // Meta models
    public val Llama3: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "meta/llama-3-70b",
        capabilities = standardCapabilities
    )
    
    public val Llama3Instruct: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "meta/llama-3-70b-instruct",
        capabilities = standardCapabilities
    )
    
    // Mistral models
    public val Mistral7B: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "mistral/mistral-7b",
        capabilities = standardCapabilities
    )
    
    public val Mixtral8x7B: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "mistral/mixtral-8x7b",
        capabilities = standardCapabilities
    )
    
    // Anthropic Vision models
    public val Claude3VisionSonnet: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "anthropic/claude-3-sonnet-vision",
        capabilities = multimodalCapabilities
    )
    
    public val Claude3VisionOpus: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "anthropic/claude-3-opus-vision",
        capabilities = multimodalCapabilities
    )
    
    public val Claude3VisionHaiku: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "anthropic/claude-3-haiku-vision",
        capabilities = multimodalCapabilities
    )
}
