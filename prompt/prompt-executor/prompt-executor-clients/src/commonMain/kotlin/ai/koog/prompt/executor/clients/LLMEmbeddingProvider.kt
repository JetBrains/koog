package ai.koog.prompt.executor.clients

import ai.koog.prompt.llm.LLModel

/**
 * Interface for generating text embeddings using an LLM provider.
 *
 * Extends [LLMProviderAware] to support shared provider-validation logic.
 */
public interface LLMEmbeddingProvider : LLMProviderAware {
    /**
     * Embeds the given text using into a vector of double-precision numbers.
     *
     * @param text The text to embed.
     * @param model The model to use for embedding. Must have the Embed capability.
     * @return A list of floating-point values representing the embedding.
     * @throws IllegalArgumentException if the model does not have the Embed capability.
     */
    public suspend fun embed(text: String, model: LLModel): List<Double>
}
