package ai.koog.a2a.model

import io.kotest.assertions.json.shouldEqualJson
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PartsSerializationTest {
    private val json = Json {
        encodeDefaults = false
        ignoreUnknownKeys = true
    }

    @Test
    fun testFileUrlPartSerialization() {
        val part: Part = FileUrlPart(url = "https://example.com/image.png", mediaType = "image/png")

        //language=JSON
        val expectedJson = """{"url": "https://example.com/image.png", "mediaType": "image/png"}"""

        json.encodeToString(part) shouldEqualJson expectedJson
        assertEquals(part, json.decodeFromString<Part>(expectedJson))
    }

    @Test
    fun testFileBytesPartSerialization() {
        val part: Part = FileBytesPart(raw = BYTES, filename = "data.bin")

        //language=JSON
        val expectedJson = """{"raw": "+//+/w==", "filename": "data.bin"}"""

        json.encodeToString(part) shouldEqualJson expectedJson
        assertEquals(part, json.decodeFromString<Part>(expectedJson))
    }

    @Test
    fun testFileBytesPartAcceptsUnpaddedAndUrlSafeBase64() {
        val expected = FileBytesPart(raw = BYTES)

        assertEquals(expected, json.decodeFromString<Part>("""{"raw": "+//+/w"}"""))
        assertEquals(expected, json.decodeFromString<Part>("""{"raw": "-__-_w=="}"""))
        assertEquals(expected, json.decodeFromString<Part>("""{"raw": "-__-_w"}"""))
    }

    @Test
    fun testFileBytesPartRejectsInvalidBase64WithSerializationException() {
        assertFailsWith<SerializationException> {
            json.decodeFromString<Part>("""{"raw": "not base64!"}""")
        }
    }

    @Test
    fun testDataPartAcceptsAnyJsonValue() {
        val values = listOf(
            JsonObject(mapOf("key" to JsonPrimitive("value"))),
            JsonArray(listOf(JsonPrimitive(1), JsonPrimitive(2))),
            JsonPrimitive("text"),
            JsonPrimitive(42),
            JsonPrimitive(true),
            JsonNull,
        )

        values.forEach { value ->
            val part: Part = DataPart(data = value)
            val partJson = buildJsonObject { put("data", value) }.toString()

            json.encodeToString(part) shouldEqualJson partJson
            assertEquals(part, json.decodeFromString<Part>(partJson))
        }
    }

    private companion object {
        val BYTES = byteArrayOf(0xfb.toByte(), 0xff.toByte(), 0xfe.toByte(), 0xff.toByte())
    }
}
