package ai.koog.agents.features.opentelemetry.extension

import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
import ai.koog.agents.features.opentelemetry.mock.MockEventBodyField
import ai.koog.agents.features.opentelemetry.mock.MockGenAIAgentEvent
import ai.koog.agents.features.opentelemetry.mock.MockGenAIAgentSpan
import ai.koog.agents.features.opentelemetry.mock.MockSpan
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class GenAIAgentEventExtTest {

    @Test
    fun `bodyFieldsToAttributes converts body fields to attributes and clears fields`() {
        val bodyFieldString = MockEventBodyField("keyString", "valueString")
        val bodyFieldList = MockEventBodyField("keyList", listOf(1, 2, 3))

        val event = MockGenAIAgentEvent(fields = listOf(bodyFieldString, bodyFieldList))
        assertEquals(0, event.attributes.size)
        assertEquals(2, event.bodyFields.size)

        event.bodyFieldsToAttributes(verbose = true)

        // Verify that all fields are converted to attributes, and fields are cleared
        assertEquals(0, event.bodyFields.size)
        assertEquals(2, event.attributes.size)

        val expectedAttributes = listOf(
            CustomAttribute("keyString", "valueString"),
            CustomAttribute("keyList", listOf(1, 2, 3))
        )

        assertContentEquals(expectedAttributes, event.attributes)
    }

    @Test
    fun `toSpanAttributes adds converted attributes to span`() {
        val bodyFieldMap = MockEventBodyField("bodyFieldMap", mapOf("test-key" to "test-value"))
        val event = MockGenAIAgentEvent(fields = listOf(bodyFieldMap))
        val span = MockGenAIAgentSpan(id = "test-span-id", name = "test-span-name")

        val mockSpan = MockSpan()
        span.span = mockSpan

        event.toSpanAttributes(span = span, verbose = false)

        val expectedAttributes = listOf(CustomAttribute("bodyFieldMap", "{\"test-key\":\"test-value\"}"))

        assertEquals(expectedAttributes.size, span.attributes.size)
        assertContentEquals(expectedAttributes, span.attributes)
        assertEquals(0, event.bodyFields.size)
    }

    @Test
    fun `bodyFieldsToBodyAttribute adds single body attribute and clears fields`() {
        val bodyFieldBoolean = MockEventBodyField("keyBoolean", true)
        val bodyFieldInt = MockEventBodyField("keyInt", 1)
        val event = MockGenAIAgentEvent(fields = listOf(bodyFieldBoolean, bodyFieldInt))

        event.bodyFieldsToBodyAttribute(verbose = true)

        val expectedAttributes = listOf(
            CustomAttribute("body", "{\"keyBoolean\":true,\"keyInt\":1}")
        )

        assertEquals(expectedAttributes.size, event.attributes.size)
        assertContentEquals(expectedAttributes, event.attributes)
        assertEquals(0, event.bodyFields.size)
    }

    @Test
    fun `bodyFieldsToBodyAttribute with mixed types`() {
        val field = MockEventBodyField("mixed", listOf("string", 1))
        val event = MockGenAIAgentEvent(fields = listOf(field))

        event.bodyFieldsToAttributes(verbose = true)

        val expected = listOf(
            CustomAttribute("mixed", listOf("string", 1))
        )

        assertEquals(0, event.bodyFields.size)
        assertEquals(expected.size, event.attributes.size)
        assertContentEquals(expected, event.attributes)
    }
}
