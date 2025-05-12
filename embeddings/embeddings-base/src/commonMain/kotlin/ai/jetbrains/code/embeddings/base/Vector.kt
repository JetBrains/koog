package ai.jetbrains.embeddings.base

import kotlinx.serialization.Serializable
import kotlin.math.sqrt

/**
 * Create a new [Vector] with the given [elements].
 *
 * The [Vector.dimension] will correspond to a number of elements.
 */
public fun vectorOf(vararg elements: Double): Vector = Vector(elements, dontCopyMarker = null)

/**
 * Represents a vector of floating-point values.
 * Used for storing embeddings of text.
 */
@Serializable
public class Vector {
    private val values: DoubleArray

    /**
     * Inner constructor wrapping the provided [elements] array.
     */
    internal constructor(elements: DoubleArray, @Suppress("UNUSED_PARAMETER") dontCopyMarker: Any?) {
        values = elements
    }

    /**
     * Constructs a new [Vector] with the same [dimension] as [elements] size and elements copied from [elements].
     */
    public constructor(elements: DoubleArray) {
        values = elements.copyOf()
    }

    /**
     * Constructs a new [Vector] with the same [dimension] as [elements] size and elements copied from [elements].
     */
    public constructor(elements: Collection<Double>) {
        values = elements.toDoubleArray()
    }

    /**
     * Returns the dimension (size) of the vector.
     */
    public val dimension: Int
        get() = values.size

    /**
     * Returns `true` when all values are zero.
     */
    public fun isNull(): Boolean = values.all { it == 0.0 }

    /**
     * Returns the magnitude of this vector.
     */
    public fun magnitude(): Double = sqrt(values.sumOf { it * it })

    /**
     * Calculates the dot product between this vector and another vector.
     *
     * @param other The other vector to calculate a product with.
     * @return The dot product of the two vectors.
     * @throws IllegalArgumentException if the vectors have different dimensions.
     */
    public infix fun dotProduct(other: Vector): Double {
        require(dimension == other.dimension) {
            "Vectors must have the same dimension (this vector's dimension is $dimension, other's is ${other.dimension})"
        }
        var prod = 0.0
        for (i in values.indices) {
            prod += values[i] * other.values[i]
        }
        return prod
    }

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
        require(this.dimension == other.dimension) {
            "Vectors must have the same dimension (this vector's dimension is $dimension, other's is ${other.dimension})"
        }

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
        require(dimension == other.dimension) {
            "Vectors must have the same dimension (this vector's dimension is $dimension, other's is ${other.dimension})"
        }

        var dist = 0.0
        for (i in values.indices) {
            val diff = values[i] - other.values[i]
            dist += diff * diff
        }
        return sqrt(dist)
    }

    public operator fun get(index: Int): Double {
        if (index !in values.indices) {
            throw IndexOutOfBoundsException("Index ($index) must be in range [0, ${values.size})")
        }
        return values[index]
    }

    public operator fun iterator(): DoubleIterator = object : DoubleIterator() {
        private var index = 0

        override fun nextDouble(): Double {
            if (!hasNext()) throw NoSuchElementException()
            return values[index++]
        }

        override fun hasNext(): Boolean = index < values.size
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Vector

        return values.contentEquals(other.values)
    }

    override fun hashCode(): Int {
        return values.contentHashCode()
    }

    override fun toString(): String {
        return "Vector(${values.contentToString()})"
    }
}
