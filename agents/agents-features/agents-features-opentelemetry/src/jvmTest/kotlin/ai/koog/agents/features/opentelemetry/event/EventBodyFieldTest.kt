package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
import ai.koog.agents.features.opentelemetry.extension.bodyFieldsToBodyAttribute
import ai.koog.agents.features.opentelemetry.mock.MockEventBodyField
import ai.koog.agents.features.opentelemetry.mock.MockGenAIAgentEvent
import ai.koog.agents.features.opentelemetry.mock.UnsupportedType
import ai.koog.agents.utils.HiddenString
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class EventBodyFieldTest {

    @Test
    fun `test toAttribute for STRING value`() {
        testToAttributeConversion(
            key = "testKey",
            value = "testValue",
            verbose = true,
            expectedKey = "body",
            expectedValue = "{\"testKey\":\"testValue\"}",
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
            expectedValue = "{\"testKey\":${list.joinToString(separator = ",", prefix = "[", postfix = "]") {
                "\"$it\""
            }}}",
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
        )
    }

    @Test
    fun `test toAttribute for the LIST OF INTEGERS`() {
        val list = listOf(1, 2, 3)

        testToAttributeConversion(
            key = "testKey",
            value = list,
            verbose = true,
            expectedKey = "body",
            expectedValue = "{\"testKey\":${list.joinToString(separator = ",", prefix = "[", postfix = "]") { "$it"}}}",
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
            expectedValue = "{\"testKey\":${list.joinToString(separator = ",", prefix = "[", postfix = "]") { "\"$it\""}}}",
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
            expectedValue = "{\"testKey\":${map.entries.joinToString(separator = ",", prefix = "{", postfix = "}") {
                "\"${it.key}\":\"${it.value}\""
            }}}",
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
            expectedValue = "{\"testKey\":\"$unsupportedType\"}",
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
        )
    }

    @Test
    fun `test toAttribute do nothing when bodyFields is empty`() {
        val event = MockGenAIAgentEvent(fields = emptyList())
        assertEquals(0, event.attributes.size)
        assertEquals(0, event.bodyFields.size)

        event.bodyFieldsToBodyAttribute(verbose = true)

        assertEquals(0, event.attributes.size)
        assertEquals(0, event.bodyFields.size)
    }

    @Test
    fun `test toAttribute filter body fields with string value when verbose is false`() {
        val bodyField = MockEventBodyField("testKey", "testValue")
        val bodyFieldContent = MockEventBodyField("testContent", "testContentValue")
        val event = MockGenAIAgentEvent(fields = listOf(bodyField, bodyFieldContent))

        event.bodyFieldsToBodyAttribute(verbose = false)

        val actualAttributes = event.attributes

        val expectedAttributes = listOf(
            CustomAttribute("body", "{\"testKey\":\"testValue\",\"testContent\":\"testContentValue\"}")
        )

        assertEquals(expectedAttributes.size, actualAttributes.size)
        assertContentEquals(expectedAttributes, actualAttributes)
    }

    @Test
    fun `test toAttribute does not filter body fields when verbose is true`() {
        val bodyField = MockEventBodyField("testKey", "testValue")
        val bodyFieldContent = MockEventBodyField("testContent", "testContentValue")
        val event = MockGenAIAgentEvent(fields = listOf(bodyField, bodyFieldContent))

        event.bodyFieldsToBodyAttribute(verbose = true)

        val actualAttributes = event.attributes

        val expectedAttributes = listOf(
            CustomAttribute(
                key = "body",
                value = "{\"testKey\":\"testValue\",\"testContent\":\"testContentValue\"}",
            )
        )

        assertEquals(expectedAttributes.size, actualAttributes.size)
        assertContentEquals(expectedAttributes, actualAttributes)
    }

    @Test
    fun `test toAttribute for HIDDEN STRING when verbose is true`() {
        testToAttributeConversion(
            key = "testKey",
            value = HiddenString("secret"),
            verbose = true,
            expectedKey = "body",
            expectedValue = "{\"testKey\":\"secret\"}",
        )
    }

    @Test
    fun `test toAttribute for HIDDEN STRING when verbose is false`() {
        testToAttributeConversion(
            key = "testKey",
            value = HiddenString("secret"),
            verbose = false,
            expectedKey = "body",
            expectedValue = "{\"testKey\":\"${HiddenString.HIDDEN_STRING_PLACEHOLDER}\"}",
        )
    }

    //region Private Methods

    private fun testToAttributeConversion(
        key: String,
        value: Any,
        verbose: Boolean,
        expectedKey: String,
        expectedValue: Any
    ) {
        val bodyField = MockEventBodyField(key, value)
        val event = MockGenAIAgentEvent(fields = listOf(bodyField))

        event.bodyFieldsToBodyAttribute(verbose = verbose)
        val actualAttribute = event.attributes.single()

        assertEquals(expectedKey, actualAttribute.key)
        assertEquals(expectedValue, actualAttribute.value)
    }

    //endregion Private Methods
}
