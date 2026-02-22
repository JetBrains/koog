package ai.koog.prompt.executor.clients

import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.EmbeddingParams

/**
 * Extension of the LLMClient interface which includes functionality for generating text embeddings
 * in addition to executing prompts and streaming outputs.
 */
public interface LLMEmbeddingProvider {
    /**
     * Embeds the given text into a vector of double-precision numbers.
     *
     * @param text The text to embed.
     * @param model The model to use for embedding. Must have the Embed capability.
     * @param params Optional provider-specific embedding parameters.
     * @return A list of floating-point values representing the embedding.
     * @throws IllegalArgumentException if the model does not have the Embed capability.
     */
    public suspend fun embed(
        text: String,
        model: LLModel,
        params: EmbeddingParams = EmbeddingParams.None
    ): List<Double>
}
