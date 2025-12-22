package ai.koog.prompt.executor.clients

import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.EmbeddingParams
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

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
     * @param params Optional embedding parameters (e.g., dimensions).
     * @return A list of floating-point values representing the embedding.
     * @throws IllegalArgumentException if the model does not have the Embed capability,
     *   or if dimensions are specified but the model lacks Embedding.Dimensions capability.
     */
    public suspend fun embed(
        text: String,
        model: LLModel,
        params: EmbeddingParams = EmbeddingParams()
    ): List<Double>

    /**
     * Embeds multiple texts in a batch.
     *
     * Default implementation processes texts in parallel using single [embed] calls.
     * Providers with native batch APIs should override this for better performance.
     *
     * @param texts The list of texts to embed.
     * @param model The model to use for embedding. Must have the Embed capability.
     * @param params Optional embedding parameters (e.g., dimensions).
     * @return A list of embeddings, one for each input text.
     * @throws IllegalArgumentException if the model does not have the Embed capability.
     */
    public suspend fun embedBatch(
        texts: List<String>,
        model: LLModel,
        params: EmbeddingParams = EmbeddingParams()
    ): List<List<Double>> = coroutineScope {
        texts.map { text ->
            async { embed(text, model, params) }
        }.awaitAll()
    }
}

