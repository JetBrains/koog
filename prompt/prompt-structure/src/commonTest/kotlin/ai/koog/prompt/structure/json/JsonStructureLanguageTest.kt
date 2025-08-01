package ai.koog.prompt.structure.json

import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JsonStructureLanguageTest {
    private val structuredLanguage = JsonStructureLanguage()

    @Serializable
    private data class SimpleData(val value: String)

    @Serializable
    private data class ComplexData(
        val name: String,
        val values: List<String>,
        val nested: SimpleData? = null
    )

    @Test
    fun testSimpleEncoding() {
        val data = SimpleData("test")
        val json = structuredLanguage.string(data)
        assertEquals("""{"value":"test"}""", json)
    }

    @Test
    fun testComplexEncoding() {
        val data = ComplexData(
            name = "test",
            values = listOf("item1", "item2"),
            nested = SimpleData("nested-value")
        )
        val json = structuredLanguage.string(data)
        val expected = """{"name":"test","values":["item1","item2"],"nested":{"value":"nested-value"}}"""
        assertEquals(expected, json)
    }

    @Test
    fun testSimpleParsing() {
        val json = """{"value":"test"}"""
        val data = structuredLanguage.parse<SimpleData>(json)
        assertEquals("test", data.value)
    }

    @Test
    fun testJsonFormatHandling() {
        val json = """
        {"value": "test with spaces"}
    """
        val data = structuredLanguage.parse<SimpleData>(json)
        assertEquals("test with spaces", data.value)
    }

    @Test
    fun testNestedObjects() {
        val json = """
        {
          "name": "parent",
          "values": ["a", "b", "c"],
          "nested": {
            "value": "child"
          }
        }
    """
        val data = structuredLanguage.parse<ComplexData>(json)
        assertEquals("parent", data.name)
        assertEquals(3, data.values.size)
        assertEquals("child", data.nested?.value)
    }

    @Test
    fun testParseError() {
        val json = """{"not_a_value":"test"}"""
        assertFailsWith<Exception> {
            structuredLanguage.parse<SimpleData>(json)
        }
    }

    @Test
    fun testMalformedJson() {
        val json = """{"value": unclosed string"""
        assertFailsWith<Exception> {
            structuredLanguage.parse<SimpleData>(json)
        }
    }

    @Test
    fun testJsonWithLinesMalformed() {
        val json = "{\"value\":\nhello\n \"unclosed string\"}"
        structuredLanguage.parse<SimpleData>(json)
    }

    @Test
    fun testJsonWithBackticks() {
        val json = """
            ```xml
            {
                "value": "unclosed string"
            }
            ```
            """
        structuredLanguage.parse<SimpleData>(json)
    }
}
