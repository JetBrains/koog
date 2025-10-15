package ai.koog.agents.memory.feature.similarity

/**
 * Supplies vector representations of text for similarity comparisons.
 *
 * Implementations can wrap existing embedding infrastructure or provide lightweight in-memory behaviour.
 */
public interface EmbeddingProvider {
    public suspend fun embed(text: String): FloatArray

    /**
     * Computes similarity score for two embeddings. Defaults to cosine similarity.
     */
    public fun similarity(lhs: FloatArray, rhs: FloatArray): Double {
        val lhsNorm = lhs.norm()
        val rhsNorm = rhs.norm()
        if (lhsNorm == 0.0 || rhsNorm == 0.0) return 0.0
        return lhs.dot(rhs) / (lhsNorm * rhsNorm)
    }

    private fun FloatArray.dot(other: FloatArray): Double {
        val size = kotlin.math.min(size, other.size)
        var result = 0.0
        for (index in 0 until size) {
            result += this[index] * other[index]
        }
        return result
    }

    private fun FloatArray.norm(): Double {
        var sum = 0.0
        for (value in this) {
            sum += value * value
        }
        return kotlin.math.sqrt(sum)
    }
}
