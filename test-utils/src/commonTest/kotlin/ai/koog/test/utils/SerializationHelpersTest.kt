package ai.koog.test.utils

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SerializationHelpersTest {

    @Serializable
    private data class TestModel(val name: String, val age: Int)

    @Test
    fun `Should deserialize valid json`() {
        val json = Json { ignoreUnknownKeys = true }
        // language=JSON
        val payload = """{"name":"John","age":30}"""
        val result = verifyDeserialization<TestModel>(payload, json)

        assertEquals(expected = "John", actual = result.name)
        assertEquals(expected = 30, actual = result.age)
    }

    @Test
    fun `Should catch invalid json`() {
        assertFailsWith<IllegalArgumentException> {
            verifyDeserialization<String>("{invalid json}")
        }
    }

    @Test
    fun `Should catch deserialization errors`() {
        // language=JSON
        val payload = """{"score":30}"""

        assertFailsWith<SerializationException> {
            verifyDeserialization<TestModel>(payload)
        }
    }

    @Test
    fun `Should serialization differences between original and deserialized model`() {
        @Serializable
        data class TestPerson(val name: String, @EncodeDefault val age: Int = 42)
        // language=JSON
        val payload = """{"name":"John"}"""

        assertFailsWith<AssertionError>(
            message = "Deserialized model should generate original payload ==> expected: <{\"name\":\"John\"}> but was: <{\"name\":\"John\",\"age\":42}>"
        ) {
            verifyDeserialization<TestPerson>(payload)
        }
    }
}
