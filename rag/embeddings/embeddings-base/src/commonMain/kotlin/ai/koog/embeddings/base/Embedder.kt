package ai.koog.embeddings.base

/**
 * Interface for text embedding operations.
 * Implementations of this interface can convert text into vector representations (embeddings)
 * and calculate the difference between two embeddings.
 */
public interface Embedder {
    /**
     * Embeds the given text into a vector representation.
     *
     * @param text The text to embed.
     * @return A vector representation of the text.
     */
    public suspend fun embed(text: String): Vector

    /**
     * Calculates the difference between two embeddings.
     * Lower values indicate more similar embeddings.
     *
     * @param embedding1 The first embedding.
     * @param embedding2 The second embedding.
     * @return A measure of difference between the embeddings.
     */
    public fun diff(embedding1: Vector, embedding2: Vector): Double
}
