package ai.koog.agents.features.opentelemetry.metric

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write
import kotlin.math.pow

internal data class EventCall(val name: String, val timeStarted: Long, val timeEnded: Long?)

internal fun EventCall.getDurationSec(): Double? = timeEnded?.minus(timeStarted)?.div((10.0.pow(6)))

internal class EventCallStorage {
    private val storage = mutableMapOf<String, EventCall>()
    private val lock = ReentrantReadWriteLock()

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    internal fun addEventCall(id: String, name: String): EventCall? {
        val currentTimestamp = System.currentTimeMillis()

        lock.write {
            if (storage.containsKey(id)) {
                logger.warn { "Tool call with id $id already exists, however is should not" }
                return null
            }

            storage.put(id, EventCall(name, currentTimestamp, null))
            return storage[id]
        }
    }

    internal fun endEventCallAndReturn(id: String): EventCall? {
        val currentTimestamp = System.currentTimeMillis()

        lock.write {
            val unfinishedEventCall = storage[id]

            if (unfinishedEventCall == null) {
                logger.warn { "Tool call with id $id does not exist" }

                return null
            }

            storage.remove(id)

            return unfinishedEventCall.copy(timeEnded = currentTimestamp)
        }
    }
}
