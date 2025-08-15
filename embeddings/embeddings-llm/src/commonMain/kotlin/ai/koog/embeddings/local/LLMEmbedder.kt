package ai.koog.embeddings.local

import ai.koog.embeddings.base.Embedder
import ai.koog.embeddings.base.Vector
import ai.koog.prompt.executor.clients.LLMEmbeddingProvider
import ai.koog.prompt.llm.LLModel

/**
 * Implementation of the [Embedder] interface that uses Ollama models for embedding text.
 *
 * @property client The Ollama model client to use for embedding text.
 */
public class LLMEmbedder(private val client: LLMEmbeddingProvider, private val model: LLModel) : Embedder {
    /**
     * Embeds the given text using the Ollama model.
     *
     * @param text The text to embed.
     * @return A vector representation of the text.
     */
    override suspend fun embed(text: String): Vector {
        return Vector(client.embed(text, model))
    }

    /**
     * Calculates the difference between two embeddings using cosine distance.
     * Cosine distance is defined as 1 - cosine similarity.
     * The result is a value between 0 and 1, where 0 means the embeddings are identical.
     *
     * @param embedding1 The first embedding.
     * @param embedding2 The second embedding.
     * @return The cosine distance between the embeddings.
     */
    override fun diff(embedding1: Vector, embedding2: Vector): Double {
        return 1.0 - embedding1.cosineSimilarity(embedding2)
    }
}
