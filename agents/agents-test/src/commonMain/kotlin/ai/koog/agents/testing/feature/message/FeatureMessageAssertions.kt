package ai.koog.agents.testing.feature.message

import ai.koog.agents.core.feature.message.FeatureMessage
import ai.koog.agents.core.feature.model.events.NodeExecutionStartingEvent
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Filters the list of `FeatureMessage` for events of the specified type `TEvent`.
 *
 * This method filters the invoking list to include only elements that are instances of the
 * specified type `TEvent`, which must be a subclass of `FeatureMessage`. An assertion is
 * performed to ensure that at least one event of the specified type is present. If no such
 * event exists, an assertion error is thrown.
 *
 * @return A list of events of type `TEvent` extracted from the invoking list.
 */
public inline fun <reified TEvent : FeatureMessage> List<FeatureMessage>.findEvents(): List<TEvent> {
    val events = this.filterIsInstance<TEvent>()
    assertTrue(
        events.isNotEmpty(),
        "Expected events of type '${TEvent::class.simpleName}' to be present in collected events. Collected events:\n" +
            this.joinToString("\n") { " - ${it::class.simpleName}" }
    )
    return events
}

/**
 * Retrieves a single event of the specified type `TEvent` from a list of `FeatureMessage`.
 *
 * This method ensures that there is exactly one event of the specified type `TEvent` present
 * in the invoking list. If no event of the specified type exists, or if more than one such event
 * is found, an assertion error is thrown. The method makes use of `findEvents` to filter the
 * list for events of the given type and then asserts that the resulting list contains exactly
 * one item.
 *
 * @return The single event of type `TEvent`.
 * @throws AssertionError if there are zero or more than one event of the specified type `TEvent`.
 */
public inline fun <reified TEvent : FeatureMessage> List<FeatureMessage>.singleEvent(): TEvent {
    val event = this.findEvents<TEvent>().singleOrNull()
    assertNotNull(
        event,
        "Expected exactly one event of type '${TEvent::class.simpleName}' to be present in collected events. Collected events:\n" +
            this.joinToString("\n") { " - ${it::class.simpleName}" }
    )

    return event
}

/**
 * Filters a list of `FeatureMessage` instances to find events of type `NodeExecutionStartingEvent`
 * associated with a specific node name.
 *
 * This function ensures that at least one event of the desired type exists in the list and further
 * ensures that events with the specified node name are found. Assertions will be triggered if
 * the conditions are not met.
 *
 * @param nodeName The name of the node for which `NodeExecutionStartingEvent` instances are being searched.
 * @return A list of `NodeExecutionStartingEvent` instances that match the provided node name.
 * @throws AssertionError If no `NodeExecutionStartingEvent` is present in the list or if no event
 * matches the specified node name.
 */
public fun List<FeatureMessage>.findNodeEvents(nodeName: String): List<NodeExecutionStartingEvent> {
    val events = this.filterIsInstance<NodeExecutionStartingEvent>()
    assertTrue(
        events.isNotEmpty(),
        "Expected events of type '${NodeExecutionStartingEvent::class.simpleName}' to be present in collected events. Collected events:\n" +
            this.joinToString("\n") { " - ${it::class.simpleName}" }
    )

    val nodeWithNameEvents = events.filter { it.nodeName == nodeName }
    assertTrue(
        nodeWithNameEvents.isNotEmpty(),
        "Expected '${NodeExecutionStartingEvent::class.simpleName}' events with node name '$nodeName' to be present in collected events. Collected node names:\n" +
            events.joinToString("\n") { " - ${it.nodeName}" }
    )

    return nodeWithNameEvents
}

/**
 * Returns a single `NodeExecutionStartingEvent` from the list of `FeatureMessage` instances
 * for the specified node name.
 *
 * This function filters the list to find events of type `NodeExecutionStartingEvent` matching the
 * given node name and ensures that exactly one such event exists. If no event or more than one
 * event matches the criteria, an exception is thrown.
 *
 * @param nodeName The name of the node for which a single `NodeExecutionStartingEvent` is required.
 * @return The single `NodeExecutionStartingEvent` associated with the specified node name.
 * @throws AssertionError If no event or multiple events matching the specified node name are found.
 */
public fun List<FeatureMessage>.singleNodeEvent(nodeName: String): NodeExecutionStartingEvent =
    this.findNodeEvents(nodeName).single()
