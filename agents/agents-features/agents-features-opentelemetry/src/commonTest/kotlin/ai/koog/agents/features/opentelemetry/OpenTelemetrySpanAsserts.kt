package ai.koog.agents.features.opentelemetry

import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.kotlin.tracing.data.SpanData
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
            "$message - Value for key '$key' should match. " + "Expected: <$value: ${value?.let { it::class.simpleName }}>, " + "Actual: <$actualValue: ${actualValue?.let { it::class.simpleName }}>."
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
    // The Kotlin OTel SDK exports spans asynchronously, so ordering is non-deterministic.
    // Match expected and actual spans by koog.event.id (unique per span) rather than by position.
    val expectedSpanNames = expectedSpans.flatMap { it.keys }.sorted()
    val actualSpanNames = actualSpans.map { it.name }.sorted()

    assertSpanNames(expectedSpanNames, actualSpanNames)

    // Build a mutable list of unmatched actual spans
    val remainingActual = actualSpans.toMutableList()

    expectedSpans.forEach { expectedSpan ->
        val expectedName = expectedSpan.keys.first()
        val expectedSpanData = expectedSpan[expectedName]!!
        val expectedAttributes = expectedSpanData["attributes"] as Map<String, Any>
        val expectedEventId = expectedAttributes["koog.event.id"]

        // Find matching actual span: same name + same koog.event.id (if present)
        val matchIndex = if (expectedEventId != null) {
            remainingActual.indexOfFirst {
                it.name == expectedName && it.attributes["koog.event.id"] == expectedEventId
            }
        } else {
            remainingActual.indexOfFirst { it.name == expectedName }
        }

        assertTrue(
            matchIndex >= 0,
            "No matching actual span found for expected span '$expectedName' with event.id=$expectedEventId"
        )

        val actualSpan = remainingActual.removeAt(matchIndex)
        val spanName = actualSpan.name

        // Attributes
        assertAttributes(spanName, expectedAttributes, actualSpan.attributes)

        // Events
        val expectedEvents = expectedSpanData["events"] as Map<String, Map<String, Any>>
        val actualEvents = actualSpan.events.associate { event ->
            event.name to event.attributes
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
