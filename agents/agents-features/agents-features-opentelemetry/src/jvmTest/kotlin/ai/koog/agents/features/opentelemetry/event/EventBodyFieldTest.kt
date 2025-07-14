package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.features.opentelemetry.mock.MockEventBodyField
import ai.koog.agents.features.opentelemetry.mock.MockGenAIAgentEvent
import ai.koog.agents.features.opentelemetry.mock.UnsupportedType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EventBodyFieldTest {

    @Test
    fun `test toAttribute for STRING value`() {
        testToAttributeConversion(
            key = "testKey",
            value = "testValue",
            verbose = true,
            expectedKey = "body",
            expectedValue = "{\"testKey\":\"testValue\"}",
            expectedVerbose = true,
        )
    }
    
    @Test
    fun `test toAttribute for CHAR value`() {
        testToAttributeConversion(
            key = "testKey",
            value = 'c',
            verbose = true,
            expectedKey = "body",
            expectedValue = "{\"testKey\":\"c\"}",
            expectedVerbose = true,
        )
    }
    
    @Test
    fun `test toAttribute for BOOLEAN value`() {
        testToAttributeConversion(
            key = "testKey",
            value = true,
            verbose = true,
            expectedKey = "body",
            expectedValue = "{\"testKey\":true}",
            expectedVerbose = true,
        )
    }
    
    @Test
    fun `test toAttribute for INT value`() {
        testToAttributeConversion(
            key = "testKey",
            value = 42,
            verbose = true,
            expectedKey = "body",
            expectedValue = "{\"testKey\":42}",
            expectedVerbose = true,
        )
    }
    
    @Test
    fun `test toAttribute for LONG value`() {
        testToAttributeConversion(
            key = "testKey",
            value = 42L,
            verbose = true,
            expectedKey = "body",
            expectedValue = "{\"testKey\":42}",
            expectedVerbose = true,
        )
    }
    
    @Test
    fun `test toAttribute for DOUBLE value`() {
        testToAttributeConversion(
            key = "testKey",
            value = 42.5,
            verbose = true,
            expectedKey = "body",
            expectedValue = "{\"testKey\":42.5}",
            expectedVerbose = true,
        )
    }
    
    @Test
    fun `test toAttribute for FLOAT value`() {
        testToAttributeConversion(
            key = "testKey",
            value = 42.5f,
            verbose = true,
            expectedKey = "body",
            expectedValue = "{\"testKey\":42.5}",
            expectedVerbose = true,
        )
    }
    
    @Test
    fun `test toAttribute for the LIST OF STRINGS`() {
        val list = listOf("value1", "value2")

        testToAttributeConversion(
            key = "testKey",
            value = list,
            verbose = true,
            expectedKey = "body",
            expectedValue = "{\"testKey\":${list.joinToString(separator = ",", prefix = "[", postfix = "]") { "\"$it\""}}}",
            expectedVerbose = true,
        )
    }
    
    @Test
    fun `test toAttribute for the LIST OF BOLEANS`() {
        val list = listOf(true, false)

        testToAttributeConversion(
            key = "testKey",
            value = list,
            verbose = true,
            expectedKey = "body",
            expectedValue = "{\"testKey\":${list.joinToString(separator = ",", prefix = "[", postfix = "]") { "$it"}}}",
            expectedVerbose = true
        )
    }
    
    @Test
    fun `test  toAttribute for the LIST OF INTEGERS`() {
        val list = listOf(1, 2, 3)

        testToAttributeConversion(
            key = "testKey",
            value = list,
            verbose = true,
            expectedKey = "body",
            expectedValue = "{\"testKey\":${list.joinToString(separator = ",", prefix = "[", postfix = "]") { "$it"}}}",
            expectedVerbose = true
        )
    }
    
    @Test
    fun `test toAttribute for the LIST with unsupported types is converted to string representation`() {
        val unsupportedType1 = UnsupportedType("value1")
        val unsupportedType2 = UnsupportedType("value2")
        val list = listOf(unsupportedType1, unsupportedType2)

        testToAttributeConversion(
            key = "testKey",
            value = list,
            verbose = true,
            expectedKey = "body",
            expectedValue = "{\"testKey\":${list.joinToString(separator = ",", prefix = "[", postfix = "]") { "$it"}}}",
            expectedVerbose = true,
        )
    }
    
    @Test
    fun `test toAttribute for the MAP is converted to string representation`() {
        val map = mapOf("key1" to "value1", "key2" to "value2")

        testToAttributeConversion(
            key = "testKey",
            value = map,
            verbose = true,
            expectedKey = "body",
            expectedValue = "{\"testKey\":${map.entries.joinToString(separator = ",", prefix = "{", postfix = "}") { "\"${it.key}\":\"${it.value}\""}}}",
            expectedVerbose = true,
        )
    }
    
    @Test
    fun `test toAttribute for unsupported type is converted to string using toString`() {
        val unsupportedType = UnsupportedType("testValue")

        testToAttributeConversion(
            key = "testKey",
            value = unsupportedType,
            verbose = true,
            expectedKey = "body",
            expectedValue = "{\"testKey\":${unsupportedType}}",
            expectedVerbose = true,
        )
    }
    
    @Test
    fun `test toAttribute for verbose property is propagated to attribute`() {
        testToAttributeConversion(
            key = "testKey",
            value = "testValue",
            verbose = true,
            expectedKey = "body",
            expectedValue = "{\"testKey\":\"testValue\"}",
            expectedVerbose = true,
        )
    }
    
    @Test
    fun `test toAttribute throws exception when bodyFields is empty`() {
        val field = MockGenAIAgentEvent(fields = emptyList(), verbose = true)

        val exception = assertFailsWith<IllegalStateException> {
            field.bodyFieldsAsAttribute()
        }

        assertEquals(
            "Unable to convert Event Body Fields into Attribute because no body fields found",
            exception.message
        )
    }
    
    @Test
    fun `test toAttribute filter body fields when verbose is false`() {
        val bodyField = MockEventBodyField("testKey", "testValue")
        val bodyFieldContent = MockEventBodyField("testContent", "testContentValue")
        val field = MockGenAIAgentEvent(fields = listOf(bodyField, bodyFieldContent), verbose = false)

        val actualAttribute = field.bodyFieldsAsAttribute()

        assertEquals("body", actualAttribute.key)
        assertEquals("{\"testKey\":\"testValue\"}", actualAttribute.value)
    }

    @Test
    fun `test toAttribute does not filter body fields when verbose is true`() {
        val bodyField = MockEventBodyField("testKey", "testValue")
        val bodyFieldContent = MockEventBodyField("testContent", "testContentValue")
        val field = MockGenAIAgentEvent(fields = listOf(bodyField, bodyFieldContent), verbose = true)

        val actualAttribute = field.bodyFieldsAsAttribute()

        assertEquals("body", actualAttribute.key)
        assertEquals("{\"testKey\":\"testValue\",\"testContent\":\"testContentValue\"}", actualAttribute.value)
    }

    //region Private Methods

    private fun testToAttributeConversion(
        key: String,
        value: Any,
        verbose: Boolean,
        expectedKey: String,
        expectedValue: Any,
        expectedVerbose: Boolean
    ) {
        val bodyField = MockEventBodyField(key, value)
        val field = MockGenAIAgentEvent(fields = listOf(bodyField), verbose = verbose)
        val actualAttribute = field.bodyFieldsAsAttribute()

        assertEquals(expectedKey, actualAttribute.key)
        assertEquals(expectedValue, actualAttribute.value)
        assertEquals(expectedVerbose, actualAttribute.verbose)
    }

    //endregion Private Methods
}