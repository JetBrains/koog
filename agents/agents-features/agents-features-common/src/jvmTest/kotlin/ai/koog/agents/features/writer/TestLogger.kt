package ai.koog.agents.features.writer

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KLoggingEventBuilder
import io.github.oshai.kotlinlogging.Level
import io.github.oshai.kotlinlogging.Marker

class TestLogger(
    override val name: String,
    val infoEnabled: Boolean = true,
    val debugEnabled: Boolean = true
) : KLogger {

    val messages = mutableListOf<String>()

    fun reset() {
        messages.clear()
    }

    val errorEnabled: Boolean = true

    val warningEnabled: Boolean = true

    override fun at(
        level: Level,
        marker: Marker?,
        block: KLoggingEventBuilder.() -> Unit
    ) {
        messages.add("[${level.name}] ${KLoggingEventBuilder().apply(block).message}")
    }

    override fun isLoggingEnabledFor(
        level: Level,
        marker: Marker?
    ): Boolean {
        return when (level) {
            Level.INFO -> infoEnabled
            Level.DEBUG -> debugEnabled
            Level.ERROR -> errorEnabled
            Level.WARN -> warningEnabled
            else -> false
        }
    }
}
