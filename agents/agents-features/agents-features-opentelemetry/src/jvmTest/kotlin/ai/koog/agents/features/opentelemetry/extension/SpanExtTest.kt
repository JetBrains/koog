package ai.koog.agents.features.opentelemetry.extension

import ai.koog.agents.features.opentelemetry.OpenTelemetrySpanAsserts.assertMapsEqual
import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
import ai.koog.agents.features.opentelemetry.mock.MockEventBodyField
import ai.koog.agents.features.opentelemetry.mock.MockGenAIAgentEvent
import ai.koog.agents.features.opentelemetry.mock.MockSpan
import ai.koog.agents.features.opentelemetry.span.SpanEndStatus
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.StatusCode
import kotlin.test.Test
import kotlin.test.assertEquals

class SpanExtTest {

    @Test
    fun `setSpanStatus sets OK by default`() {
        val span = MockSpan()
        span.setSpanStatus()
        assertEquals(StatusCode.OK, span.status)
        assertEquals("", span.statusDescription)
    }

    @Test
    fun `setSpanStatus sets provided code and description`() {
        val span = MockSpan()
        span.setSpanStatus(SpanEndStatus(StatusCode.ERROR, "test description"))
        assertEquals(StatusCode.ERROR, span.status)
        assertEquals("test description", span.statusDescription)
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
            AttributeKey.stringKey("keyString") to "valueString",
            AttributeKey.longKey("keyInt") to 1L,
            AttributeKey.booleanKey("keyBoolean") to true
        )

        assertEquals(expectedAttributes.size, actualAttributes.size)
        assertMapsEqual(expectedAttributes, actualAttributes)
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

        val actualEventAttributes = actualEvents.values.first().asMap()
        val expectedEvents = mapOf(
            AttributeKey.stringKey("keyString") to "valueString",
            AttributeKey.longKey("keyInt") to 1L,
        )

        assertEquals(expectedEvents.size, actualEventAttributes.size)
        assertMapsEqual(expectedEvents, actualEventAttributes)
    }
}
