package ai.koog.agents.features.opentelemetry.metric

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write
import kotlin.math.pow

internal data class ToolCall(val name: String, val timeStarted: Long, val timeEnded: Long?)

internal fun ToolCall.getDurationSec(): Double? = timeEnded?.minus(timeStarted)?.div((10.0.pow(6)))

internal class ToolCallStorage {
    private val storage = mutableMapOf<String, ToolCall>()
    private val lock = ReentrantReadWriteLock()

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    internal fun addToolCall(id: String, name: String): ToolCall? {
        val currentTimestamp = System.currentTimeMillis()

        lock.write {
            if (storage.containsKey(id)) {
                logger.warn { "Tool call with id $id already exists, however is should not" }
                return null
            }

            storage.put(id, ToolCall(name, currentTimestamp, null))
            return storage[id]
        }
    }

    internal fun endToolCallAndReturn(id: String): ToolCall? {
        val currentTimestamp = System.currentTimeMillis()

        lock.write {
            val unfinishedToolCall = storage[id]

            if (unfinishedToolCall == null) {
                logger.warn { "Tool call with id $id does not exist" }

                return null
            }

            storage.remove(id)

            return unfinishedToolCall.copy(timeEnded = currentTimestamp)
        }
    }
}
