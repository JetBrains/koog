package ai.jetbrains.embeddings.local

/**
 * Enum representing available Ollama embedding models.
 *
 * Models are sourced from https://ollama.com/blog/embedding-models and other official Ollama resources.
 * Each model has a specific purpose and performance characteristics.
 *
 * @property id The string identifier used by Ollama API to reference this model
 */
enum class OllamaEmbeddingModel(val id: String) {
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
    NOMIC_EMBED_TEXT("nomic-embed-text"),

    /**
     * All MiniLM embedding model, a lightweight and efficient model.
     *
     * Parameters: 33M
     * Dimensions: 384
     * Context Length: 512
     * Performance: Fast inference with good quality for general text embeddings
     * Tradeoffs: Smaller model size with reduced context length, but very efficient
     */
    ALL_MINILM("all-minilm"),

    /**
     * Multilingual E5 embedding model, supports multiple languages.
     *
     * Parameters: 300M
     * Dimensions: 768
     * Context Length: 512
     * Performance: Strong performance across 100+ languages
     * Tradeoffs: Larger model size but provides excellent multilingual capabilities
     */
    MULTILINGUAL_E5("zylonai/multilingual-e5-large"),

    /**
     * BGE Large embedding model, optimized for English text.
     *
     * Parameters: 335M
     * Dimensions: 1024
     * Context Length: 512
     * Performance: Excellent for English text retrieval and semantic search
     * Tradeoffs: Larger model size but provides high-quality embeddings
     */
    BGE_LARGE("bge-large"),

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
    MXBAI_EMBED_LARGE("mxbai-embed-large");


    override fun toString(): String = id
}
