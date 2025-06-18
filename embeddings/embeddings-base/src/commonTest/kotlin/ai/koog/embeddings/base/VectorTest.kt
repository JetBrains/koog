package ai.koog.embeddings.base

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VectorTest {
    @Test
    fun testDimension() {
        val vector = Vector(listOf(1.0, 2.0, 3.0))
        assertEquals(3, vector.dimension)
    }

    @Test
    fun testCosineSimilarity_identicalVectors() {
        val vector1 = Vector(listOf(1.0, 2.0, 3.0))
        val vector2 = Vector(listOf(1.0, 2.0, 3.0))
        assertEquals(1.0, vector1.cosineSimilarity(vector2), 0.0001)
    }

    @Test
    fun testCosineSimilarity_orthogonalVectors() {
        val vector1 = Vector(listOf(1.0, 0.0, 0.0))
        val vector2 = Vector(listOf(0.0, 1.0, 0.0))
        assertEquals(0.0, vector1.cosineSimilarity(vector2), 0.0001)
    }

    @Test
    fun testCosineSimilarity_oppositeVectors() {
        val vector1 = Vector(listOf(1.0, 2.0, 3.0))
        val vector2 = Vector(listOf(-1.0, -2.0, -3.0))
        assertEquals(-1.0, vector1.cosineSimilarity(vector2), 0.0001)
    }

    @Test
    fun testCosineSimilarity_differentDimensions() {
        val vector1 = Vector(listOf(1.0, 2.0, 3.0))
        val vector2 = Vector(listOf(1.0, 2.0))
        assertFailsWith<IllegalArgumentException> {
            vector1.cosineSimilarity(vector2)
        }
    }

    @Test
    fun testEuclideanDistance_identicalVectors() {
        val vector1 = Vector(listOf(1.0, 2.0, 3.0))
        val vector2 = Vector(listOf(1.0, 2.0, 3.0))
        assertEquals(0.0, vector1.euclideanDistance(vector2), 0.0001)
    }

    @Test
    fun testEuclideanDistance_differentVectors() {
        val vector1 = Vector(listOf(1.0, 2.0, 3.0))
        val vector2 = Vector(listOf(4.0, 5.0, 6.0))
        assertEquals(5.196, vector1.euclideanDistance(vector2), 0.001)
    }

    @Test
    fun testEuclideanDistance_differentDimensions() {
        val vector1 = Vector(listOf(1.0, 2.0, 3.0))
        val vector2 = Vector(listOf(1.0, 2.0))
        assertFailsWith<IllegalArgumentException> {
            vector1.euclideanDistance(vector2)
        }
    }
}
