package ai.koog.utils.serializers

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ByteArrayAsBase64SerializerTest {

    private val subject = ByteArrayAsBase64Serializer()

    @Serializable
    private data class Container(
        @Serializable(with = ByteArrayAsBase64Serializer::class) val data: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as Container

            return data.contentEquals(other.data)
        }

        override fun hashCode(): Int {
            return data.contentHashCode()
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun `should serialize and deserialize bytes`() {
        val data = Random.nextBytes(32)
        val serializedForm = Json.encodeToString(subject, data)

        assertEquals(
            actual = serializedForm,
            expected = "\"${Base64.encode(data)}\""
        )

        val restored = Json.decodeFromString(subject, serializedForm)

        assertContentEquals(
            actual = restored,
            expected = data
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun `should serialize and deserialize container`() {
        val data = Random.nextBytes(32)
        val container = Container(data)

        val encodedContainer = Json.encodeToString(container)

        assertEquals(
            actual = encodedContainer,
            expected = "{\"data\":\"${Base64.encode(data)}\"}"
        )

        val restored: Container = Json.decodeFromString(encodedContainer)

        assertContentEquals(
            actual = restored.data,
            expected = data
        )
    }
}
