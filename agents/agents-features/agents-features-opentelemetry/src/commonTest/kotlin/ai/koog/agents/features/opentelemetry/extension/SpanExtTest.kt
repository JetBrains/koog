package ai.koog.agents.features.opentelemetry.extension

import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
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
        assertEquals("test description", status.description)
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
}
