package ai.koog.embeddings.local

import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

/**
 * Ollama embedding models.
 *
 * Models are sourced from https://ollama.com/blog/embedding-models and other official Ollama resources.
 * Each model has a specific purpose and performance characteristics.
 */
public object OllamaEmbeddingModels {
    /**
     * Nomic's text embedding model, optimized for text embeddings.
     * A good general-purpose embedding model.
     *
     * Parameters: 137M
     * Dimensions: 768
     * Context Length: 8192
     * Performance: High-quality embeddings for semantic search and text similarity tasks
     * Tradeoffs: Balanced between quality and efficiency
     */
    public val NOMIC_EMBED_TEXT: LLModel = LLModel(
        provider = LLMProvider.Ollama,
        id = "nomic-embed-text",
        capabilities = listOf(LLMCapability.Embed)
    )

    /**
     * All MiniLM embedding model, a lightweight and efficient model.
     *
     * Parameters: 33M
     * Dimensions: 384
     * Context Length: 512
     * Performance: Fast inference with good quality for general text embeddings
     * Tradeoffs: Smaller model size with reduced context length, but very efficient
     */
    public val ALL_MINI_LM: LLModel = LLModel(
        provider = LLMProvider.Ollama,
        id = "all-minilm",
        capabilities = listOf(LLMCapability.Embed)
    )

    /**
     * Multilingual E5 embedding model, supports multiple languages.
     *
     * Parameters: 300M
     * Dimensions: 768
     * Context Length: 512
     * Performance: Strong performance across 100+ languages
     * Tradeoffs: Larger model size but provides excellent multilingual capabilities
     */
    public val MULTILINGUAL_E5: LLModel = LLModel(
        provider = LLMProvider.Ollama,
        id = "zylonai/multilingual-e5-large",
        capabilities = listOf(LLMCapability.Embed)
    )

    /**
     * BGE Large embedding model, optimized for English text.
     *
     * Parameters: 335M
     * Dimensions: 1024
     * Context Length: 512
     * Performance: Excellent for English text retrieval and semantic search
     * Tradeoffs: Larger model size but provides high-quality embeddings
     */
    public val BGE_LARGE: LLModel = LLModel(
        provider = LLMProvider.Ollama,
        id = "bge-large",
        capabilities = listOf(LLMCapability.Embed)
    )

    /**
     * Represents the model ID for the MXBAI Embed Large model.
     *
     * This model ID identifies the "mxbai-embed-large" embedding configuration
     * within the Ollama framework, which is designed for creating high-dimensional
     * embeddings of textual data.
     *
     * It can be used in components that require referencing or interacting
     * with this specific model configuration.
     */
    public val MXBAI_EMBED_LARGE: LLModel = LLModel(
        provider = LLMProvider.Ollama,
        id = "mxbai-embed-large",
        capabilities = listOf(LLMCapability.Embed)
    )
}