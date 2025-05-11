package ai.grazie.code.agents.local.features.common.writer

import ai.grazie.code.agents.local.features.common.message.FeatureMessage
import ai.grazie.code.agents.local.features.common.message.FeatureMessageProcessor
import ai.grazie.utils.mpp.LoggerFactory
import ai.grazie.utils.mpp.MPPLogger

/**
 * An abstract base class for implementing a stream feature provider that logs incoming feature messages
 * into a provided logger instance.
 *
 * @param targetLogger The [MPPLogger] instance used for feature messages to be streamed into.
 */
abstract class FeatureMessageLogWriter(
    protected val targetLogger: MPPLogger,
    protected val logLevel: LogLevel = LogLevel.INFO
) : FeatureMessageProcessor() {

    companion object {
        private val logger = LoggerFactory.create("ai.grazie.code.agents.local.features.common.writer.FeatureMessageLogWriter")
    }

    enum class LogLevel { INFO, DEBUG }

    init {
        if (!isTargetLogLevelEnabled(logLevel, targetLogger)) {
            logger.info { "Please note: Desired log level: '${logLevel.name}' is disabled for target logger" }
        }
    }

    /**
     * Converts the incoming [FeatureMessage] into a target logger message.
     */
    abstract fun FeatureMessage.toLoggerMessage(): String

    override suspend fun processMessage(message: FeatureMessage) {
        val logString = "Received feature message [${message.messageType.value}]: ${message.toLoggerMessage()}"

        when (logLevel) {
            LogLevel.INFO -> targetLogger.info { logString }
            LogLevel.DEBUG -> targetLogger.debug { logString }
        }
    }

    override suspend fun close() { }

    private fun isTargetLogLevelEnabled(targetLogLevel: LogLevel, targetLogger: MPPLogger): Boolean {
        return when (targetLogLevel) {
            LogLevel.INFO -> targetLogger.infoEnabled
            LogLevel.DEBUG -> targetLogger.debugEnabled
        }
    }
}
