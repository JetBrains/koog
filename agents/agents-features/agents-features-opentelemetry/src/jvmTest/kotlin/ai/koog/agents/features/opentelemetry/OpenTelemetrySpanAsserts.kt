package ai.koog.agents.features.opentelemetry

import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.sdk.trace.data.SpanData
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val logger = KotlinLogging.logger { }

internal fun assertMapsEqual(expected: Map<*, *>, actual: Map<*, *>, message: String = "") {
    assertEquals(expected.size, actual.size, "$message - Map sizes should be equal")

    expected.forEach { (key, value) ->
        assertTrue(actual.containsKey(key), "$message - Key '$key' should exist in actual map")

        val actualValue = actual[key]
        assertEquals(
            value,
            actualValue,
            "$message - Value for key '$key' should match. " + "Expected: <$value: ${value?.javaClass?.simpleName}>, " + "Actual: <$actualValue: ${actualValue?.javaClass?.simpleName}>."
        )
    }
}

/**
 * Expected Span:
 *   Map<SpanName, Map<Any>>
 *       where Any = "attributes" or "events"
 *       attributes: Map<AttributeKey, AttributeValue>
 *       events: Map<EventName, Attributes>
 *           Attributes: Map<AttributeKey, AttributeValue>
 */
@Suppress("UNCHECKED_CAST")
internal fun assertSpans(expectedSpans: List<Map<String, Map<String, Any>>>, actualSpans: List<SpanData>) {
    // Span names
    val expectedSpanNames = expectedSpans.flatMap { it.keys }
    val actualSpanNames = actualSpans.map { it.name }

    assertSpanNames(expectedSpanNames, actualSpanNames)

    // Span attributes + events
    actualSpans.forEachIndexed { index, actualSpan ->

        val expectedSpan = expectedSpans[index]

        val expectedSpanData = expectedSpan[actualSpan.name]
        assertNotNull(expectedSpanData, "Span (name: ${actualSpan.name}) not found in expected spans")

        val spanName = actualSpan.name

        // Attributes
        val expectedAttributes = expectedSpanData["attributes"] as Map<String, Any>
        val actualAttributes = actualSpan.attributes.asMap().asSequence().associate {
            it.key.key to it.value
        }

        assertAttributes(spanName, expectedAttributes, actualAttributes)

        // Events
        val expectedEvents = expectedSpanData["events"] as Map<String, Map<String, Any>>
        val actualEvents = actualSpan.events.associate { event ->
            val actualEventAttributes = event.attributes.asMap().asSequence().associate { (key, value) ->
                key.key to value
            }
            event.name to actualEventAttributes
        }

        assertEventsForSpan(spanName, expectedEvents, actualEvents)
    }
}

internal fun assertSpanNames(expectedSpanNames: List<String>, actualSpanNames: List<String>) {
    assertEquals(
        expectedSpanNames.size,
        actualSpanNames.size,
        "Expected collection of spans should be the same size\n" +
            "Expected:\n${expectedSpanNames.joinToString("\n") { " - $it" }}\n" +
            "Actual:\n${actualSpanNames.joinToString("\n") { " - $it"}}"
    )
    assertContentEquals(
        expectedSpanNames,
        actualSpanNames,
        "Expected collection of spans should be the same as actual"
    )
}

/**
 * Event:
 *   Map<EventName, Attributes> -> Map<EventName, Map<AttributeKey, AttributeValue>>
 */
internal fun assertEventsForSpan(
    spanName: String,
    expectedEvents: Map<String, Map<String, Any>>,
    actualEvents: Map<String, Map<String, Any>>
) {
    logger.info {
        "Asserting events for the Span (name: $spanName).\nExpected events:\n$expectedEvents\nActual events:\n$actualEvents"
    }

    assertEquals(
        expectedEvents.size,
        actualEvents.size,
        "Expected collection of events should be the same size for the span (name: $spanName)"
    )

    actualEvents.forEach { (actualEventName, actualEventAttributes) ->

        logger.info { "Asserting event (name: $actualEventName) for the Span (name: $spanName)" }

        val expectedEventAttributes = expectedEvents[actualEventName]
        assertNotNull(
            expectedEventAttributes,
            "Event (name: $actualEventName) not found in expected events for span (name: $spanName)"
        )

        assertAttributes(spanName, expectedEventAttributes, actualEventAttributes)
    }
}

/**
 * Attribute:
 *   Map<AttributeKey, AttributeValue>
 */
internal fun assertAttributes(
    spanName: String,
    expectedAttributes: Map<String, Any>,
    actualAttributes: Map<String, Any>
) {
    logger.debug {
        "Asserting attributes for the Span (name: $spanName).\nExpected attributes:\n$expectedAttributes\nActual attributes:\n$actualAttributes"
    }

    assertEquals(
        expectedAttributes.size,
        actualAttributes.size,
        "Expected collection of attributes should be the same size for the span (name: $spanName)\n" +
            "Expected: <${expectedAttributes.toList().joinToString(
                prefix = "\n{\n",
                postfix = "\n}",
                separator = "\n"
            ) { pair ->
                "  ${pair.first}=${pair.second}"
            }}>,\n" +
            "Actual: <${actualAttributes.toList().joinToString(
                prefix = "\n{\n",
                postfix = "\n}",
                separator = "\n"
            ) { pair ->
                "  ${pair.first}=${pair.second}"
            }}>"
    )

    actualAttributes.forEach { (actualArgName: String, actualArgValue: Any) ->

        logger.debug { "Find expected attribute (name: $actualArgName) for the Span (name: $spanName)" }
        val expectedArgValue = expectedAttributes[actualArgName]

        assertNotNull(
            expectedArgValue,
            "Attribute (name: $actualArgName) not found in expected attributes for span (name: $spanName)"
        )

        when (actualArgValue) {
            is Map<*, *> -> {
                assertMapsEqual(expectedArgValue as Map<*, *>, actualArgValue)
            }

            is Iterable<*> -> {
                assertContentEquals(expectedArgValue as Iterable<*>, actualArgValue.asIterable())
            }

            else -> {
                assertEquals(
                    expectedArgValue,
                    actualArgValue,
                    "Attribute values should be the same (span: $spanName, attribute key: $actualArgName)\n" +
                        "Expected: <$expectedArgValue>,\n" +
                        "Actual: <$actualArgValue>"
                )
            }
        }
    }
}
