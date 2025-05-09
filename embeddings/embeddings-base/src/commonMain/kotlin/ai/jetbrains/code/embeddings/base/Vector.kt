package ai.jetbrains.embeddings.base

import kotlinx.serialization.Serializable
import kotlin.math.sqrt

/**
 * Represents a vector of floating-point values.
 * Used for storing embeddings of text.
 *
 * @property values The floating-point values that make up the vector.
 */
@Serializable
data class Vector(val values: List<Double>) {
    /**
     * Returns the dimension (size) of the vector.
     */
    val dimension: Int
        get() = values.size

    fun isNull(): Boolean = values.all { it == 0.0 }

    fun magnitude(): Double = sqrt(values.sumOf { it * it.toDouble() })

    infix fun dotProduct(other: Vector): Double = values.zip(other.values).sumOf { (a, b) -> a * b.toDouble() }

    /**
     * Calculates the cosine similarity between this vector and another vector.
     * The result is a value between -1 and 1, where 1 means the vectors are identical,
     * 0 means they are orthogonal, and -1 means they are completely opposite.
     *
     * @param other The other vector to compare with.
     * @return The cosine similarity between the two vectors.
     * @throws IllegalArgumentException if the vectors have different dimensions.
     */
    fun cosineSimilarity(other: Vector): Double {
        require(this.dimension == other.dimension) { "Vectors must have the same dimension" }

        if (this.isNull() || other.isNull()) return 0.0

        return (this dotProduct other) / (this.magnitude() * other.magnitude())
    }

    /**
     * Calculates the Euclidean distance between this vector and another vector.
     * The result is a non-negative value, where 0 means the vectors are identical.
     *
     * @param other The other vector to compare with.
     * @return The Euclidean distance between the two vectors.
     * @throws IllegalArgumentException if the vectors have different dimensions.
     */
    fun euclideanDistance(other: Vector): Double {
        require(dimension == other.dimension) { "Vectors must have the same dimension" }

        return values.zip(other.values)
            .sumOf { (a, b) -> (a - b).toDouble().let { it * it } }
            .let { sqrt(it) }
    }
}