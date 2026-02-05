package ai.koog.agents.features.opentelemetry.metric

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

internal class MetricEventStorage {
    private val storage = ConcurrentHashMap<String, MetricEvent>()

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    internal fun startEvent(metricEvent: MetricEvent): Boolean {
        val prevValue = storage.putIfAbsent(metricEvent.id, metricEvent)
        if (prevValue == null) {
            logger.warn { "Metric Event with id=${metricEvent.id} already exists" }

            return false
        }

        return true
    }

    private fun getPairedEvent(eventId: String): MetricEvent? {
        val prevValue = storage.remove(eventId)
        if (prevValue == null) {
            logger.warn { "Metric Event with id=$eventId does not exist" }

            return null
        }

        return prevValue
    }

    private fun <T : MetricEvent> endEvent(closingEvent: MetricEvent): T? {
        getPairedEvent(closingEvent.id)?.let { it as? T }?.let {
            return it
        }

        logger.warn { "Paired Event with id=${closingEvent.id} is not found" }
        return null
    }

    internal fun endEvent(closingEvent: LLMCallEnded): Pair<LLMCallStarted, LLMCallEnded>? =
        endEvent<LLMCallStarted>(closingEvent)?.let { it to closingEvent }

    internal fun endEvent(closingEvent: ToolCallEnded): Pair<ToolCallStarted, ToolCallEnded>? =
        endEvent<ToolCallStarted>(closingEvent)?.let { it to closingEvent }
}
