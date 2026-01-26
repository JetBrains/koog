package ai.koog.agents.features.opentelemetry.metric

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write


internal class MetricEventStorage {
    private val storage = mutableMapOf<String, MetricEvent>()
    private val lock = ReentrantReadWriteLock()

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    internal fun startEvent(metricEvent: MetricEvent): Boolean {
        val id = metricEvent.id

        lock.write {
            if (storage.containsKey(id)) {
                logger.warn { "Metric event with id $id already exists, it could not be added to the storage" }
                return false
            }

            storage[id] = metricEvent
            return true
        }
    }

    private fun getPairedEvent(eventId: String): MetricEvent? {
        lock.write {
            val metricEvent = storage[eventId]

            if (metricEvent == null) {
                logger.warn { "Metric Event with id=$eventId does not exist" }

                return null
            }

            storage.remove(eventId)
            return metricEvent
        }
    }

    private fun <T : MetricEvent> finishEvent(closingEvent: MetricEvent): T? {
        getPairedEvent(closingEvent.id)?.let { it as? T }?.let {
            return it
        }

        logger.warn { "Paired Event with id=${closingEvent.id} is not found" }
        return null
    }

    internal fun finishEvent(closingEvent: LLMCallEnded): Pair<LLMCallStarted, LLMCallEnded>? =
        finishEvent<LLMCallStarted>(closingEvent)?.let { it to closingEvent }

    internal fun finishEvent(closingEvent: ToolCallEnded): Pair<ToolCallStarted, ToolCallEnded>? =
        finishEvent<ToolCallStarted>(closingEvent)?.let { it to closingEvent }
}
