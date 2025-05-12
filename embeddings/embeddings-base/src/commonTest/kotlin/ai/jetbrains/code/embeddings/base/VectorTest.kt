package ai.jetbrains.embeddings.base

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VectorTest {
    @Test
    fun testDimension() {
        val vector = vectorOf(1.0, 2.0, 3.0)
        assertEquals(3, vector.dimension)
    }

    @Test
    fun testCosineSimilarity_identicalVectors() {
        val vector1 = vectorOf(1.0, 2.0, 3.0)
        val vector2 = vectorOf(1.0, 2.0, 3.0)
        assertEquals(1.0, vector1.cosineSimilarity(vector2), 0.0001)
    }

    @Test
    fun testCosineSimilarity_orthogonalVectors() {
        val vector1 = vectorOf(1.0, 0.0, 0.0)
        val vector2 = vectorOf(0.0, 1.0, 0.0)
        assertEquals(0.0, vector1.cosineSimilarity(vector2), 0.0001)
    }

    @Test
    fun testCosineSimilarity_oppositeVectors() {
        val vector1 = vectorOf(1.0, 2.0, 3.0)
        val vector2 = vectorOf(-1.0, -2.0, -3.0)
        assertEquals(-1.0, vector1.cosineSimilarity(vector2), 0.0001)
    }

    @Test
    fun testCosineSimilarity_differentDimensions() {
        val vector1 = vectorOf(1.0, 2.0, 3.0)
        val vector2 = vectorOf(1.0, 2.0)
        assertFailsWith<IllegalArgumentException> {
            vector1.cosineSimilarity(vector2)
        }
    }

    @Test
    fun testEuclideanDistance_identicalVectors() {
        val vector1 = vectorOf(1.0, 2.0, 3.0)
        val vector2 = vectorOf(1.0, 2.0, 3.0)
        assertEquals(0.0, vector1.euclideanDistance(vector2), 0.0001)
    }

    @Test
    fun testEuclideanDistance_differentVectors() {
        val vector1 = vectorOf(1.0, 2.0, 3.0)
        val vector2 = vectorOf(4.0, 5.0, 6.0)
        assertEquals(5.196, vector1.euclideanDistance(vector2), 0.001)
    }

    @Test
    fun testEuclideanDistance_differentDimensions() {
        val vector1 = vectorOf(1.0, 2.0, 3.0)
        val vector2 = vectorOf(1.0, 2.0)
        assertFailsWith<IllegalArgumentException> {
            vector1.euclideanDistance(vector2)
        }
    }

    @Test
    fun testDotProduct_differentVectors() {
        val vector1 = vectorOf(1.0, 2.0, 3.0)
        val vector2 = vectorOf(3.0, 2.0, 5.0)

        assertEquals(22.0, vector1.dotProduct(vector2), absoluteTolerance = 1e-6)
    }

    @Test
    fun testDotProduct_differentDimensions() {
        val vector1 = vectorOf(1.0, 2.0, 3.0)
        val vector2 = vectorOf(1.0, 2.0)
        assertFailsWith<IllegalArgumentException> {
            vector1 dotProduct vector2
        }
    }

    @Test
    fun testVectorIteration() {
        val vector1 = vectorOf(1.0, 2.0, 3.0)
        val observed = buildList {
            for (e in vector1) {
                add(e)
            }
        }
        assertEquals(listOf(1.0, 2.0, 3.0), observed)
    }

    @Test
    fun testVectorElementAccess() {
        val v = vectorOf(1.0, 2.0, 3.0)
        assertEquals(1.0, v[0])
        assertEquals(2.0, v[1])
        assertEquals(3.0, v[2])

        assertFailsWith<IndexOutOfBoundsException> { v[-1] }
        assertFailsWith<IndexOutOfBoundsException> { v[3] }
    }

    @Test
    fun testSerialization() {
        val v = vectorOf(1.1, 2.1, 3.1)
        val serialRepresentation = Json.encodeToString(v)
        assertEquals("{\"values\":[1.1,2.1,3.1]}", serialRepresentation)
    }

    @Test
    fun testDeserialization() {
        val v: Vector = Json.decodeFromString("{\"values\":[1.0,2.0,3.0]}")
        assertEquals(vectorOf(1.0, 2.0, 3.0), v)
    }
}
