package ai.koog.koogelis.logging

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KLoggingEvent
import io.github.oshai.kotlinlogging.KLoggingEventBuilder
import io.github.oshai.kotlinlogging.KotlinLoggingConfiguration
import io.github.oshai.kotlinlogging.Level
import io.github.oshai.kotlinlogging.Marker
import io.github.oshai.kotlinlogging.isLoggingEnabled

class KLoggerImpl(val logger: KoogelisLogger, override val name: String): KLogger {

    override fun isLoggingEnabledFor(level: Level, marker: Marker?): Boolean {
        return level.isLoggingEnabled()
    }

    override fun at(level: Level, marker: Marker?, block: KLoggingEventBuilder.() -> Unit) {
        if (isLoggingEnabledFor(level, marker)) {
            KLoggingEventBuilder().apply(block).run {
                when (level) {
                    Level.OFF -> Unit
                    else -> KotlinLoggingConfiguration.appender.log(KLoggingEvent(level, marker, name, this))
                }
            }
        }
    }

    override fun info(message: () -> Any?) {
        logger.info(message.toStringSafe())
    }

    override fun debug(message: () -> Any?) {
        logger.debug(message.toStringSafe())
    }

    override fun trace(message: () -> Any?) {
        logger.trace(message.toStringSafe())
    }

    override fun warn(message: () -> Any?) {
        logger.warn(message.toStringSafe())
    }

    override fun error(message: () -> Any?) {
        logger.error(message.toStringSafe())
    }

    internal fun (() -> Any?).toStringSafe(): String {
        return try {
            invoke().toString()
        } catch (e: Exception) {
            "Log message invocation failed: $e"
        }
    }
}