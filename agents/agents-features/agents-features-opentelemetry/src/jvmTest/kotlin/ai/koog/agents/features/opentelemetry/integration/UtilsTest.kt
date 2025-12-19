package ai.koog.agents.features.opentelemetry.integration

import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
import ai.koog.agents.features.opentelemetry.event.EventBodyFields
import ai.koog.agents.features.opentelemetry.mock.MockAttribute
import ai.koog.agents.features.opentelemetry.mock.MockEventBodyField
import ai.koog.agents.features.opentelemetry.mock.MockGenAIAgentEvent
import ai.koog.agents.features.opentelemetry.mock.MockGenAIAgentSpan
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class UtilsTest {

    //region bodyFieldsToCustomAttribute

    @Test
    fun `test bodyFieldsToCustomAttribute convert matching body fields into attributes`() {
        val span = MockGenAIAgentSpan(id = "test-span-id", name = "test-span-name")

        val bodyFieldId = EventBodyFields.Id("id-body-field")
        val bodyFieldContent = EventBodyFields.Content("content-body-field")
        val mockBodyField = MockEventBodyField("mock-body-field-key", "mock-body-field-value")

        val originalEvent = MockGenAIAgentEvent(
            name = "event",
            fields = listOf(bodyFieldId, bodyFieldContent, mockBodyField)
        )

        span.addEvent(originalEvent)

        span.bodyFieldsToCustomAttribute<EventBodyFields.Content>(originalEvent) { field ->
            CustomAttribute("new.${field.key}", "new.${field.value}")
        }

        val expectedAttributes = listOf(
            CustomAttribute("new.${bodyFieldContent.key}", "new.${bodyFieldContent.value}"),
        )

        val actualAttributes = span.attributes

        assertEquals(expectedAttributes.size, actualAttributes.size)
        assertContentEquals(expectedAttributes, actualAttributes)

        val expectedEvents = listOf(originalEvent)
        val actualEvents = span.events

        assertEquals(expectedEvents.size, actualEvents.size)
        assertContentEquals(expectedEvents, actualEvents)

        val expectedBodyFields = listOf(bodyFieldId, mockBodyField)
        val actualBodyFields = originalEvent.bodyFields

        assertEquals(expectedBodyFields.size, actualBodyFields.size)
        assertContentEquals(expectedBodyFields, actualBodyFields)
    }

    @Test
    fun `test bodyFieldsToCustomAttribute does nothing when there are no matching fields`() {
        val span = MockGenAIAgentSpan(id = "test-span-id", name = "test-span-name")

        val mockEventBody1 = MockEventBodyField("mock-event-body-field-key-1", "mock-event-body-field-value-1")
        val mockEventBody2 = MockEventBodyField("mock-event-body-field-key-2", 42)

        val originalEvent = MockGenAIAgentEvent(
            name = "event",
            fields = listOf(mockEventBody1, mockEventBody2)
        )

        span.addEvent(originalEvent)

        span.bodyFieldsToCustomAttribute<EventBodyFields.Content>(originalEvent) { field ->
            CustomAttribute("new.${field.key}", field.value)
        }

        val actualAttributes = span.attributes
        val expectedAttributes = emptyList<CustomAttribute>()

        assertEquals(expectedAttributes.size, actualAttributes.size)
        assertContentEquals(expectedAttributes, actualAttributes)

        val actualEvents = span.events
        val expectedEvents = listOf(originalEvent)

        assertEquals(expectedEvents.size, actualEvents.size)
        assertContentEquals(expectedEvents, actualEvents)

        val actualBodyFields = originalEvent.bodyFields
        val expectedBodyFields = listOf(mockEventBody1, mockEventBody2)

        assertEquals(expectedBodyFields.size, actualBodyFields.size)
        assertContentEquals(expectedBodyFields, actualBodyFields)
    }

    //endregion bodyFieldsToCustomAttribute

    //region replaceBodyFields

    @Test
    fun `test replaceBodyFields processes each matching field and removes them from event`() {
        val span = MockGenAIAgentSpan(id = "test-span-id", name = "test-span-name")

        val bodyFieldId = EventBodyFields.Id("id-body-field")
        val bodyFieldContent = EventBodyFields.Content("content-body-field")
        val mockBodyField = MockEventBodyField("mock-body-field-key", "mock-body-field-value")

        val originalEvent = MockGenAIAgentEvent(
            name = "event",
            fields = listOf(bodyFieldId, bodyFieldContent, mockBodyField)
        )

        span.addEvent(originalEvent)

        span.replaceBodyFields<EventBodyFields.Content>(originalEvent) { bodyField ->
            addAttribute(CustomAttribute("new.${bodyField.key}", "new.${bodyField.value}"))
        }

        val actualAttributes = span.attributes
        val expectedAttributes = listOf(
            CustomAttribute("new.${bodyFieldContent.key}", "new.${bodyFieldContent.value}"),
        )

        assertEquals(expectedAttributes.size, actualAttributes.size)
        assertContentEquals(expectedAttributes, actualAttributes)

        val actualEvents = span.events
        val expectedEvents = listOf(originalEvent)

        assertEquals(expectedEvents.size, actualEvents.size)
        assertContentEquals(expectedEvents, actualEvents)

        val actualBodyFields = originalEvent.bodyFields
        val expectedBodyFields = listOf(bodyFieldId, mockBodyField)

        assertEquals(expectedBodyFields.size, actualBodyFields.size)
        assertContentEquals(expectedBodyFields, actualBodyFields)
    }

    @Test
    fun `test replaceBodyFields replace multiple fields`() {
        val span = MockGenAIAgentSpan(id = "test-span-id", name = "test-span-name")

        val bodyFieldContent = EventBodyFields.Content("content-body-field")
        val mockBodyField1 = MockEventBodyField("mock-body-field-key-1", "mock-body-field-value-1")
        val mockBodyField2 = MockEventBodyField("mock-body-field-key-2", "mock-body-field-value-2")

        val originalEvent = MockGenAIAgentEvent(
            name = "event",
            fields = listOf(bodyFieldContent, mockBodyField1, mockBodyField2)
        )

        span.addEvent(originalEvent)

        span.replaceBodyFields<MockEventBodyField>(originalEvent) { bodyField ->
            addAttribute(CustomAttribute("new.${bodyField.key}", "new.${bodyField.value}"))
        }

        val actualAttributes = span.attributes
        val expectedAttributes = listOf(
            CustomAttribute("new.${mockBodyField1.key}", "new.${mockBodyField1.value}"),
            CustomAttribute("new.${mockBodyField2.key}", "new.${mockBodyField2.value}"),
        )

        assertEquals(expectedAttributes.size, actualAttributes.size)
        assertContentEquals(expectedAttributes, actualAttributes)

        val actualEvents = span.events
        val expectedEvents = listOf(originalEvent)

        assertEquals(expectedEvents.size, actualEvents.size)
        assertContentEquals(expectedEvents, actualEvents)

        val actualBodyFields = originalEvent.bodyFields
        val expectedBodyFields = listOf(bodyFieldContent)

        assertEquals(expectedBodyFields.size, actualBodyFields.size)
        assertContentEquals(expectedBodyFields, actualBodyFields)
    }

    @Test
    fun `replaceBodyFields does nothing when there are no matching fields`() {
        val span = MockGenAIAgentSpan(id = "test-span-id", name = "test-span-name")

        val mockBodyField1 = MockEventBodyField("mock-body-field-key-1", "mock-body-field-value-1")
        val mockBodyField2 = MockEventBodyField("mock-body-field-key-2", "mock-body-field-value-2")

        val originalEvent = MockGenAIAgentEvent(
            name = "event",
            fields = listOf(mockBodyField1, mockBodyField2)
        )

        span.addEvent(originalEvent)

        span.replaceBodyFields<EventBodyFields.Content>(originalEvent) { bodyField ->
            addAttribute(CustomAttribute("new.${bodyField.key}", "new.${bodyField.value}"))
        }

        val actualAttributes = span.attributes
        val expectedAttributes = emptyList<CustomAttribute>()

        assertEquals(expectedAttributes.size, actualAttributes.size)
        assertContentEquals(expectedAttributes, actualAttributes)

        val actualEvents = span.events
        val expectedEvents = listOf(originalEvent)

        assertEquals(expectedEvents.size, actualEvents.size)
        assertContentEquals(expectedEvents, actualEvents)

        val actualBodyFields = originalEvent.bodyFields
        val expectedBodyFields = listOf(mockBodyField1, mockBodyField2)

        assertEquals(expectedBodyFields.size, actualBodyFields.size)
    }

    //endregion replaceBodyFields

    //region replaceAttributes

    @Test
    fun `test replace attributes when span has no attribute`() {
        val span = MockGenAIAgentSpan(id = "test-span-id", name = "test-span-name")

        span.replaceAttributes<CustomAttribute> { _ ->
            CustomAttribute("newKey", "newValue")
        }

        assertEquals(0, span.attributes.size)
    }

    @Test
    fun `test replace attributes when multiple attributes of different types exist`() {
        val span = MockGenAIAgentSpan(id = "test-span-id", name = "test-span-name")

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
        val span = MockGenAIAgentSpan(id = "test-span-id", name = "test-span-name")

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
        val span = MockGenAIAgentSpan(id = "test-span-id", name = "test-span-name")

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
        val span = MockGenAIAgentSpan(id = "test-span-id", name = "test-span-name")

        val customAttribute1 = CustomAttribute("customAttributeKey1", "customAttributeValue1")
        val customAttribute2 = CustomAttribute("customAttributeKey2", "customAttributeValue2")
        val actualAttributes = listOf(customAttribute1, customAttribute2)
        span.addAttributes(actualAttributes)

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
