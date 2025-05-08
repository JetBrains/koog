package ai.jetbrains.code.prompt.executor.clients.openrouter

import ai.jetbrains.code.prompt.llm.LLMCapability
import ai.jetbrains.code.prompt.llm.LLMProvider
import ai.jetbrains.code.prompt.llm.LLModel

/**
 * OpenRouter models
 * Models available through the OpenRouter API
 */
object OpenRouterModels {
    private val standardCapabilities: List<LLMCapability> = listOf(
        LLMCapability.Temperature,
        LLMCapability.Schema.JSON.Full,
        LLMCapability.Speculation,
        LLMCapability.Tools
    )

    /**
     * Free model for testing and development
     */
    val Phi4Reasoning = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "microsoft/phi-4-reasoning:free",
        capabilities = standardCapabilities
    )

    // Multimodal capabilities (including vision)
    private val multimodalCapabilities: List<LLMCapability> = standardCapabilities + LLMCapability.Vision
    
    // Anthropic models
    val Claude3Opus = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "anthropic/claude-3-opus",
        capabilities = multimodalCapabilities
    )
    
    val Claude3Sonnet = LLModel(
        provider = LLMProvider.OpenRouter, 
        id = "anthropic/claude-3-sonnet", 
        capabilities = multimodalCapabilities
    )

    val Claude3Haiku = LLModel(
        provider = LLMProvider.OpenRouter, 
        id = "anthropic/claude-3-haiku", 
        capabilities = multimodalCapabilities
    )
    
    // OpenAI models
    val GPT4 = LLModel(
        provider = LLMProvider.OpenRouter, 
        id = "openai/gpt-4", 
        capabilities = standardCapabilities
    )
    
    val GPT4o = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "openai/gpt-4o",
        capabilities = multimodalCapabilities
    )
    
    val GPT4Turbo = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "openai/gpt-4-turbo",
        capabilities = multimodalCapabilities
    )
    
    val GPT35Turbo = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "openai/gpt-3.5-turbo",
        capabilities = standardCapabilities
    )
    
    // Google models
    val Gemini14Pro = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "google/gemini-1.5-pro",
        capabilities = multimodalCapabilities
    )
    
    val Gemini15Flash = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "google/gemini-1.5-flash",
        capabilities = multimodalCapabilities
    )
    
    // Meta models
    val Llama3 = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "meta/llama-3-70b",
        capabilities = standardCapabilities
    )
    
    val Llama3Instruct = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "meta/llama-3-70b-instruct",
        capabilities = standardCapabilities
    )
    
    // Mistral models
    val Mistral7B = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "mistral/mistral-7b",
        capabilities = standardCapabilities
    )
    
    val Mixtral8x7B = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "mistral/mixtral-8x7b",
        capabilities = standardCapabilities
    )
    
    // Anthropic Vision models
    val Claude3VisionSonnet = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "anthropic/claude-3-sonnet-vision",
        capabilities = multimodalCapabilities
    )
    
    val Claude3VisionOpus = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "anthropic/claude-3-opus-vision",
        capabilities = multimodalCapabilities
    )
    
    val Claude3VisionHaiku = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "anthropic/claude-3-haiku-vision",
        capabilities = multimodalCapabilities
    )
}