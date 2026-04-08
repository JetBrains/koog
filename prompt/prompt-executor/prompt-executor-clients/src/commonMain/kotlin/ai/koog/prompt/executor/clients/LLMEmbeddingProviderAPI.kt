package ai.koog.prompt.executor.clients

import ai.koog.prompt.llm.LLModel

/**
 * API for [LLMEmbeddingProvider]
 */
public interface LLMEmbeddingProviderAPI {
    /**
     * Embeds the given text using the given model into a vector of double-precision numbers.
     *
     * @param text The text to embed.
     * @param model The model to use for embedding. Must have the Embed capability.
     * @return A list of floating-point values representing the embedding.
     * @throws IllegalArgumentException if the model does not have the Embed capability.
     */
    public suspend fun embed(text: String, model: LLModel): List<Double> {
        throw UnsupportedOperationException("Not implemented for this client")
    }

    /**
     * Embeds the given input using the given model into a vector of double-precision numbers.
     *
     * @param inputs The input to embed.
     * @param model The model to use for embedding. Must have the Embed capability.
     * @return A list of lists of floating-point values representing the embedding.
     * Each inner list represents a single input embedding.
     * @throws IllegalArgumentException if the model does not have the Embed capability.
     */
    public suspend fun embed(inputs: List<String>, model: LLModel): List<List<Double>> {
        throw UnsupportedOperationException("Not implemented for this client")
    }
}
