package ai.koog.agents.features.opentelemetry.extension

import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
import ai.koog.agents.features.opentelemetry.mock.MockEventBodyField
import ai.koog.agents.features.opentelemetry.mock.MockGenAIAgentEvent
import ai.koog.agents.features.opentelemetry.mock.MockSpan
import io.opentelemetry.kotlin.tracing.StatusData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpanExtTest {

    @Test
    fun `setSpanStatus sets OK by default`() {
        val span = MockSpan()
        span.setSpanStatus(endStatus = null)
        assertEquals(StatusData.Ok, span.status)
    }

    @Test
    fun `setSpanStatus sets provided code and description`() {
        val span = MockSpan()
        span.setSpanStatus(endStatus = StatusData.Error("test description"))
        val status = span.status
        assertTrue(status is StatusData.Error)
        assertEquals("test description", (status as StatusData.Error).description)
    }

    @Test
    fun `setAttributes on Span writes all attributes`() {
        val span = MockSpan()
        val attributes = listOf(
            CustomAttribute("keyString", "valueString"),
            CustomAttribute("keyInt", 1),
            CustomAttribute("keyBoolean", true),
        )

        span.setAttributes(attributes, verbose = true)

        val actualAttributes = span.collectedAttributes
        val expectedAttributes = mapOf(
            "keyString" to "valueString",
            "keyInt" to 1L,
            "keyBoolean" to true
        )

        assertEquals(expectedAttributes.size, actualAttributes.size)
        assertEquals(expectedAttributes, actualAttributes)
    }

    @Test
    fun `setEvents converts body fields to attributes and adds events`() {
        val span = MockSpan()
        val event = MockGenAIAgentEvent().apply {
            addAttribute(CustomAttribute("keyString", "valueString"))
            addBodyField(MockEventBodyField("keyInt", 1))
        }

        span.setEvents(listOf(event), verbose = true)

        val actualEvents = span.collectedEvents
        assertEquals(1, actualEvents.size)

        val actualEventAttributes = actualEvents[0].attributes
        val expectedEvents = mapOf(
            "keyString" to "valueString",
            "keyInt" to 1L,
        )

        assertEquals(expectedEvents.size, actualEventAttributes.size)
        assertEquals(expectedEvents, actualEventAttributes)
    }
}
