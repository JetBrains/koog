package ai.koog.agents.features.opentelemetry.attribute

import ai.koog.agents.features.opentelemetry.mock.UnsupportedType
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.AttributeType
import io.opentelemetry.api.internal.InternalAttributeKeyImpl
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AttributeExtensionTest {

    @Test
    fun `test convert STRING attribute to sdk map`() =
        testAttributeType(
            key = "stringKey",
            value = "stringValue",
            expectedType = AttributeType.STRING,
            expectedValue = "stringValue"
        )

    @Test
    fun `test convert INTEGER attribute to sdk map`() =
        testAttributeType(
            key = "intKey",
            value = 123,
            expectedType = AttributeType.LONG,
            expectedValue = 123L
        )

    @Test
    fun `test convert BOOLEAN attribute to sdk map`() =
        testAttributeType(
            key = "booleanKey",
            value = false,
            expectedType = AttributeType.BOOLEAN,
            expectedValue = false
        )

    @Test
    fun `test convert LONG attribute to sdk map`() =
        testAttributeType(
            key = "longKey",
            value = 123L,
            expectedType = AttributeType.LONG,
            expectedValue = 123L
        )

    @Test
    fun `test convert DOUBLE attribute to sdk map`() =
        testAttributeType(
            key = "doubleKey",
            value = 123.45,
            expectedType = AttributeType.DOUBLE,
            expectedValue = 123.45
        )

    @Test
    fun `test convert ARRAY OF STRING attribute to sdk map`() =
        testAttributeType(
            key = "stringArrayKey",
            value = listOf("stringValue1", "stringValue2"),
            expectedType = AttributeType.STRING_ARRAY,
            expectedValue = listOf("stringValue1", "stringValue2")
        )

    @Test
    fun `test convert ARRAY OF INTEGER attribute to sdk map`() =
        testAttributeType(
            key = "intArrayKey",
            value = listOf(123, 456),
            expectedType = AttributeType.LONG_ARRAY,
            expectedValue = listOf(123L, 456L)
        )

    @Test
    fun `test convert ARRAY OF BOOLEAN attribute to sdk map`() =
        testAttributeType(
            key = "booleanArrayKey",
            value = listOf(true, false),
            expectedType = AttributeType.BOOLEAN_ARRAY,
            expectedValue = listOf(true, false)
        )

    @Test
    fun `test convert ARRAY OF LONG attribute to sdk map`() =
        testAttributeType(
            key = "longArrayKey",
            value = listOf(123L, 456L),
            expectedType = AttributeType.LONG_ARRAY,
            expectedValue = listOf(123L, 456L)
        )

    @Test
    fun `test convert ARRAY OF DOUBLE attribute to sdk map`() =
        testAttributeType(
            key = "doubleArrayKey",
            value = listOf(123.45, 678.90),
            expectedType = AttributeType.DOUBLE_ARRAY,
            expectedValue = listOf(123.45, 678.90)
        )

    @Test
    fun `test convert UNSUPPORTED type attribute to sdk map`() {
        val unsupportedTypeObj = UnsupportedType("unsupportedType")
        val testCustomAttribute = CustomAttribute("unsupportedKey", unsupportedTypeObj)

        val expectedAttributes = listOf(testCustomAttribute)
        val throwable = assertThrows<IllegalStateException> {
            expectedAttributes.toSdkAttributes()
        }

        assertEquals(
            "Attribute 'unsupportedKey' has unsupported type for value: ${UnsupportedType::class.simpleName}",
            throwable.message
        )
    }

    @Test
    fun `test convert LIST OF UNSUPPORTED type attribute to sdk map`() {
        val unsupportedTypeObj1 = UnsupportedType("unsupportedType1")
        val unsupportedTypeObj2 = UnsupportedType("unsupportedType2")
        val unsupportedTypeList = listOf(unsupportedTypeObj1, unsupportedTypeObj2)

        val testCustomAttribute = CustomAttribute("unsupportedKey", unsupportedTypeList)

        val expectedAttributes = listOf(testCustomAttribute)
        val throwable = assertThrows<IllegalStateException> {
            expectedAttributes.toSdkAttributes()
        }

        assertEquals(
            "Attribute 'unsupportedKey' has unsupported type for List values: ${UnsupportedType::class.simpleName}",
            throwable.message
        )
    }

    @Test
    fun `test converting list of attributes with different value types to sdk map`() {
        val testCustomAttribute1 = CustomAttribute("stringKey", "stringValue")
        val testCustomAttribute2 = CustomAttribute("intKey", 123)

        val expectedAttributes = listOf(testCustomAttribute1, testCustomAttribute2)
        val actualSdkAttributes = expectedAttributes.toSdkAttributes()

        assertEquals(expectedAttributes.size, actualSdkAttributes.size())

        val actualEntries = actualSdkAttributes.asMap().entries.toTypedArray().sortedBy { it.key.key }

        val actualIntEntry = actualEntries[0]
        assertSdkAttribute(
            actualKey = actualIntEntry.key,
            actualValue = actualIntEntry.value,
            expectedType = AttributeType.LONG,
            expectedKey = testCustomAttribute2.key,
            expectedValue = (testCustomAttribute2.value as Int).toLong()
        )

        val actualStringEntry = actualEntries[1]
        assertSdkAttribute(
            actualKey = actualStringEntry.key,
            actualValue = actualStringEntry.value,
            expectedType = AttributeType.STRING,
            expectedKey = testCustomAttribute1.key,
            expectedValue = testCustomAttribute1.value
        )
    }

    @Test
    fun `test converting list of attributes include unsupported type to sdk map`() {
        val unsupportedTypeObj = UnsupportedType("unsupportedType")
        val testCustomAttribute = CustomAttribute("stringKey", "stringValue")
        val unsupportedAttribute = CustomAttribute("unsupportedKey", unsupportedTypeObj)

        val expectedAttributes = listOf(testCustomAttribute, unsupportedAttribute)
        val throwable = assertThrows<IllegalStateException> {
            expectedAttributes.toSdkAttributes()
        }

        assertEquals(
            throwable.message,
            "Attribute 'unsupportedKey' has unsupported type for value: ${UnsupportedType::class.simpleName}"
        )
    }

    private fun assertSdkAttribute(
        actualKey: AttributeKey<*>,
        actualValue: Any,
        expectedType: AttributeType,
        expectedKey: String,
        expectedValue: Any
    ) {
        assertEquals(
            InternalAttributeKeyImpl::class,
            actualKey::class,
            "Check key: '$expectedKey'. Expected sdk key type: ${InternalAttributeKeyImpl::class.simpleName}, but got: ${actualKey::class.simpleName}"
        )

        assertEquals(
            expectedType,
            actualKey.type,
            "Check key: '$expectedKey'. Expected type: $expectedType, but got: ${actualKey.type}"
        )

        assertEquals(
            expectedKey,
            actualKey.key,
            "Check key: '$expectedKey'. Expected key: $expectedKey, but got: ${actualKey.key}"
        )

        assertEquals(
            expectedValue,
            actualValue,
            "Check key: '$expectedKey'. Expected value: $expectedValue, but got: $actualValue"
        )
    }

    private fun <TActual, TExpected> testAttributeType(
        key: String,
        value: TActual,
        expectedType: AttributeType,
        expectedValue: TExpected
    ) where TActual : Any, TExpected : Any {
        val testCustomAttribute = CustomAttribute(key, value)

        val expectedAttributes = listOf(testCustomAttribute)
        val actualSdkAttributes = expectedAttributes.toSdkAttributes()

        assertEquals(expectedAttributes.size, actualSdkAttributes.size())

        val actualSdkAttributeEntry = actualSdkAttributes.asMap().entries.firstOrNull()
        assertNotNull(actualSdkAttributeEntry)

        assertSdkAttribute(
            actualKey = actualSdkAttributeEntry.key,
            actualValue = actualSdkAttributeEntry.value,
            expectedType = expectedType,
            expectedKey = key,
            expectedValue = expectedValue
        )
    }
}
