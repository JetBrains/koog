package ai.koog.embeddings.base

import kotlinx.serialization.Serializable
import kotlin.math.sqrt

/**
 * Represents a vector of floating-point values.
 * Used for storing embeddings of text.
 *
 * @property values The floating-point values that make up the vector.
 */
@Serializable
public data class Vector(val values: List<Double>) {
    /**
     * Returns the dimension (size) of the vector.
     */
    public val dimension: Int
        get() = values.size

    private companion object {
        /**
         * Implements the Kahan summation algorithm for more accurate summation of floating-point numbers.
         * This algorithm significantly reduces numerical errors when adding many floating-point values.
         *
         * @param values The collection of values to sum
         * @param valueSelector A function that extracts or transforms the value before summation
         * @return The sum with improved precision
         */
        private inline fun <T> kahanSum(values: Collection<T>, valueSelector: (T) -> Double): Double {
            var sum = 0.0
            var compensation = 0.0 // Compensation for lost low-order bits

            for (value in values) {
                val y = valueSelector(value) - compensation // Compensated value
                val t = sum + y // Next sum
                compensation = (t - sum) - y // Compute the error
                sum = t // Store the result
            }

            return sum
        }
    }

    public fun isNull(): Boolean = values.all { it == 0.0 }

    public fun magnitude(): Double = sqrt(kahanSum(values) { it * it })

    public infix fun dotProduct(other: Vector): Double = kahanSum(values.zip(other.values)) { (a, b) -> a * b }

    /**
     * Calculates the cosine similarity between this vector and another vector.
     * The result is a value between -1 and 1, where 1 means the vectors are identical,
     * 0 means they are orthogonal, and -1 means they are completely opposite.
     *
     * @param other The other vector to compare with.
     * @return The cosine similarity between the two vectors.
     * @throws IllegalArgumentException if the vectors have different dimensions.
     */
    public fun cosineSimilarity(other: Vector): Double {
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
    public fun euclideanDistance(other: Vector): Double {
        require(dimension == other.dimension) { "Vectors must have the same dimension" }

        return sqrt(kahanSum(values.zip(other.values)) { (a, b) -> (a - b).let { it * it } })
    }
}
