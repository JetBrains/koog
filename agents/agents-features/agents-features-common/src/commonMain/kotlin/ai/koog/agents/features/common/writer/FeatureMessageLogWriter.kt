package ai.koog.agents.features.common.writer

import ai.koog.agents.features.common.message.FeatureMessage
import ai.koog.agents.features.common.message.FeatureMessageProcessor
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * An abstract base class for implementing a stream feature provider that logs incoming feature messages
 * into a provided logger instance.
 *
 * @param targetLogger The [KLogger] instance used for feature messages to be streamed into.
 */
public abstract class FeatureMessageLogWriter(
    protected val targetLogger: KLogger,
    protected val logLevel: LogLevel = LogLevel.INFO
) : FeatureMessageProcessor() {

    private companion object {
        private val logger = KotlinLogging.logger {  }
    }

    /**
     * Defines the logging levels supported by the system.
     *
     * The `LogLevel` enum is used to specify the level of detail to be logged.
     * This can be useful for controlling the verbosity of log output and filtering
     * messages based on their importance or purpose.
     *
     * `INFO`: Represents informational messages that highlight the overall
     * application flow, such as initialization or significant lifecycle events.
     *
     * `DEBUG`: Represents detailed debugging messages used for diagnosing issues
     * or understanding the internal state and behavior of the application.
     */
    public enum class LogLevel { INFO, DEBUG }

    init {
        if (!isTargetLogLevelEnabled(logLevel, targetLogger)) {
            logger.info { "Please note: Desired log level: '${logLevel.name}' is disabled for target logger" }
        }
    }

    /**
     * Converts the incoming [FeatureMessage] into a target logger message.
     */
    public abstract fun FeatureMessage.toLoggerMessage(): String

    override suspend fun processMessage(message: FeatureMessage) {
        val logString = "Received feature message [${message.messageType.value}]: ${message.toLoggerMessage()}"

        when (logLevel) {
            LogLevel.INFO -> targetLogger.info { logString }
            LogLevel.DEBUG -> targetLogger.debug { logString }
        }
    }

    override suspend fun close() { }

    private fun isTargetLogLevelEnabled(targetLogLevel: LogLevel, targetLogger: KLogger): Boolean {
        return when (targetLogLevel) {
            LogLevel.INFO -> targetLogger.isInfoEnabled()
            LogLevel.DEBUG -> targetLogger.isDebugEnabled()
        }
    }
}
