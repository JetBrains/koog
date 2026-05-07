package ai.koog.agents.features.opentelemetry.integration

import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
import ai.koog.agents.features.opentelemetry.mock.MockAttribute
import ai.koog.agents.features.opentelemetry.mock.MockContext
import ai.koog.agents.features.opentelemetry.mock.MockSpan
import ai.koog.agents.features.opentelemetry.span.GenAIAgentSpan
import ai.koog.agents.features.opentelemetry.span.SpanType
import io.opentelemetry.kotlin.tracing.SpanKind
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class GenAIAgentSpanUtilsTest {

    //region replaceAttributes

    @Test
    fun `test replace attributes when span has no attribute`() {
        val span = GenAIAgentSpan(
            type = SpanType.CREATE_AGENT,
            parentSpan = null,
            id = "test-span-id",
            name = "test-span-name",
            span = MockSpan(),
            context = MockContext(),
            kind = SpanKind.INTERNAL,
            attributes = emptyList(),
        )

        span.replaceAttributes<CustomAttribute> { _ ->
            CustomAttribute("newKey", "newValue")
        }

        assertEquals(0, span.attributes.size)
    }

    @Test
    fun `test replace attributes when multiple attributes of different types exist`() {
        val span = GenAIAgentSpan(
            type = SpanType.CREATE_AGENT,
            parentSpan = null,
            id = "test-span-id",
            name = "test-span-name",
            span = MockSpan(),
            context = MockContext(),
            kind = SpanKind.INTERNAL,
            attributes = emptyList(),
        )

        val customAttribute = CustomAttribute("customAttributeKey", "customAttributeValue")
        val mockAttribute = MockAttribute("mockAttributeKey", 123)
        span.addAttributes(listOf(customAttribute, mockAttribute))

        val newAttribute = CustomAttribute("newAttributeKey", "newAttributeValue")

        span.replaceAttributes<CustomAttribute> { _ ->
            newAttribute
        }

        val expectedAttributes = listOf(mockAttribute, newAttribute)
        val actualAttributes = span.attributes

        assertEquals(expectedAttributes.size, actualAttributes.size)
        assertContentEquals(expectedAttributes, actualAttributes)
    }

    @Test
    fun `test replace attributes when span has no attributes of expected type`() {
        val span = GenAIAgentSpan(
            type = SpanType.CREATE_AGENT,
            parentSpan = null,
            id = "test-span-id",
            name = "test-span-name",
            span = MockSpan(),
            context = MockContext(),
            kind = SpanKind.INTERNAL,
            attributes = emptyList(),
        )

        val originalAttribute = MockAttribute("mockAttributeKey", "mockAttributeValue")
        span.addAttribute(originalAttribute)

        span.replaceAttributes<CustomAttribute> { _ ->
            CustomAttribute("customAttributeKey", "customAttributeValue")
        }

        val expectedAttributes = listOf(originalAttribute)
        val actualAttributes = span.attributes

        assertEquals(expectedAttributes.size, actualAttributes.size)
        assertContentEquals(expectedAttributes, actualAttributes)
    }

    @Test
    fun `test replace single attribute when span has one attribute`() {
        val span = GenAIAgentSpan(
            type = SpanType.CREATE_AGENT,
            parentSpan = null,
            id = "test-span-id",
            name = "test-span-name",
            span = MockSpan(),
            context = MockContext(),
            kind = SpanKind.INTERNAL,
            attributes = emptyList(),
        )

        val originalAttribute = CustomAttribute("customAttributeKey", "customAttributeValue")
        span.addAttribute(originalAttribute)

        val newAttribute = CustomAttribute("newCustomAttributeKey", "newCustomAttributeValue")

        span.replaceAttributes<CustomAttribute> { _ ->
            newAttribute
        }

        val expectedAttributes = listOf(newAttribute)
        val actualAttributes = span.attributes

        assertEquals(expectedAttributes.size, actualAttributes.size)
        assertContentEquals(expectedAttributes, actualAttributes)
    }

    @Test
    fun `test replace single attribute when more than one attribute exist`() {
        val span = GenAIAgentSpan(
            type = SpanType.CREATE_AGENT,
            parentSpan = null,
            id = "test-span-id",
            name = "test-span-name",
            span = MockSpan(),
            context = MockContext(),
            kind = SpanKind.INTERNAL,
            attributes = emptyList(),
        )

        val customAttribute1 = CustomAttribute("customAttributeKey1", "customAttributeValue1")
        val customAttribute2 = CustomAttribute("customAttributeKey2", "customAttributeValue2")
        val attributesToAdd = listOf(customAttribute1, customAttribute2)
        span.addAttributes(attributesToAdd)

        val newCustomAttribute = CustomAttribute("newCustomAttributeKey", "newCustomAttributeValue")
        val newMockAttribute = MockAttribute("newMockAttributeKey", "newMockAttributeValue")

        span.replaceAttributes<CustomAttribute> { existingAttribute ->
            if (existingAttribute.key == customAttribute1.key) {
                return@replaceAttributes newCustomAttribute
            }

            if (existingAttribute.key == customAttribute2.key) {
                return@replaceAttributes newMockAttribute
            }

            existingAttribute
        }

        val expectedAttributes = listOf(newCustomAttribute, newMockAttribute)
        val actualAttributesAfterReplacement = span.attributes

        assertEquals(expectedAttributes.size, actualAttributesAfterReplacement.size)
        assertContentEquals(expectedAttributes, actualAttributesAfterReplacement)
    }

    //endregion replaceAttributes
}
