package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.features.opentelemetry.mock.MockEventBodyField
import ai.koog.agents.features.opentelemetry.mock.UnsupportedType
import kotlin.test.Test
import kotlin.test.assertEquals

class EventBodyFieldTest {

    @Test
    fun `test toAttribute for STRING value`() {
        testToAttributeConversion(
            key = "testKey",
            value = "testValue",
            expectedValue = "\"testValue\""
        )
    }
    
    @Test
    fun `test toAttribute for CHAR value`() {
        testToAttributeConversion(
            key = "testKey",
           value = 'c',
           expectedValue = "\"c\""
        )
    }
    
    @Test
    fun `test toAttribute for BOOLEAN value`() {
        testToAttributeConversion(
            key = "testKey",
            value = true,
            expectedValue = true
        )
    }
    
    @Test
    fun `test toAttribute for INT value`() {
        testToAttributeConversion(
            key = "testKey",
            value = 42,
            expectedValue = 42
        )
    }
    
    @Test
    fun `test toAttribute for LONG value`() {
        testToAttributeConversion(
            key = "testKey",
            value = 42L,
            expectedValue = 42L
        )
    }
    
    @Test
    fun `test toAttribute for DOUBLE value`() {
        testToAttributeConversion(
            key = "testKey",
            value = 42.5,
            expectedValue = 42.5
        )
    }
    
    @Test
    fun `test toAttribute for FLOAT value`() {
        testToAttributeConversion(
            key = "testKey",
            value = 42.5f,
            expectedValue = 42.5f
        )
    }
    
    @Test
    fun `test toAttribute for the LIST OF STRINGS`() {
        val list = listOf("value1", "value2")
        testToAttributeConversion(
            key = "testKey",
            value = list,
            expectedValue = list
        )
    }
    
    @Test
    fun `test toAttribute for the LIST OF BOLEANS`() {
        val list = listOf(true, false)

        testToAttributeConversion(
            key = "testKey",
            value = list,
            expectedValue = list
        )
    }
    
    @Test
    fun `test  toAttribute for the LIST OF INTEGERS`() {
        val list = listOf(1, 2, 3)

        testToAttributeConversion(
            key = "testKey",
            value = list,
            expectedValue = list
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
            expectedValue = "[UnsupportedType(value=value1), UnsupportedType(value=value2)]",
        )
    }
    
    @Test
    fun `test toAttribute for the MAP is converted to string representation`() {
        val map = mapOf("key1" to "value1", "key2" to "value2")

        testToAttributeConversion(
            key = "testKey",
            value = map,
            expectedValue = "{\"key1\": \"value1\", \"key2\": \"value2\"}",
        )
    }
    
    @Test
    fun `test toAttribute for unsupported type is converted to string using toString`() {
        val unsupportedType = UnsupportedType("testValue")

        testToAttributeConversion(
            key = "testKey",
            value = unsupportedType,
            expectedValue = unsupportedType.toString()
        )
    }
    
    @Test
    fun `test toAttribute for verbose property is propagated to attribute`() {
        testToAttributeConversion(
            key = "testKey",
            value = "testValue",
            verbose = true,
            expectedValue = "\"testValue\"",
            expectedVerbose = true,
        )
    }

    //region Private Methods

    private fun testToAttributeConversion(
        key: String,
        value: Any,
        verbose: Boolean = false,
        expectedValue: Any,
        expectedVerbose: Boolean = false
    ) {
        val field = MockEventBodyField(key, value, verbose)
        val actualAttribute = field.toAttribute()

        assertEquals(key, actualAttribute.key)
        assertEquals(expectedValue, actualAttribute.value)
        assertEquals(expectedVerbose, actualAttribute.verbose)
    }

    //endregion Private Methods
}