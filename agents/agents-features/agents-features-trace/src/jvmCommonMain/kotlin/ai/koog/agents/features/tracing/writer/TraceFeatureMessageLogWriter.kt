package ai.koog.agents.features.tracing.writer

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.feature.message.FeatureMessage
import ai.koog.agents.core.feature.writer.FeatureMessageLogWriter
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.slf4j.toKLogger
import org.slf4j.Logger

/**
 * Implementation of [FeatureMessageLogWriter] that handles logging of feature messages
 * with trace-level detail using a standardized or custom log message format.
 *
 * @constructor Initializes an instance of [TraceFeatureMessageLogWriter].
 * @param targetLogger The [KLogger] instance to output trace logs.
 * @param logLevel The log level used for trace events. Defaults to [LogLevel.INFO].
 * @param format A lambda function for custom formatting of [FeatureMessage] into
 * a loggable string, or `null` to use a default format.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public actual class TraceFeatureMessageLogWriter @JvmOverloads actual constructor(
    targetLogger: KLogger,
    logLevel: LogLevel,
    format: ((FeatureMessage) -> String)?
) : FeatureMessageLogWriter(targetLogger, logLevel) {
    private val delegate = TraceFeatureMessageLogWriterImpl(targetLogger, logLevel, format)

    actual override fun FeatureMessage.toLoggerMessage(): String = with(delegate) { toLoggerMessage() }

    /**
     * Companion object with factories for [TraceFeatureMessageLogWriter]
     * */
    public companion object {

        /**
         * Creates a new instance of [TraceFeatureMessageLogWriter] for handling trace events.
         *
         * @param logger The SLF4J-compatible logger ([KLogger]) used to log trace events.
         * @param logLevel The logging level to apply for trace events. Defaults to [LogLevel.INFO].
         * @param format An optional custom formatter for converting [FeatureMessage] objects to log messages.
         * If `null`, a default formatting strategy is used.
         * @return A new instance of [TraceFeatureMessageLogWriter].
         */
        public fun create(
            logger: KLogger,
            logLevel: LogLevel = LogLevel.INFO,
            format: ((FeatureMessage) -> String)? = null,
        ): TraceFeatureMessageLogWriter = TraceFeatureMessageLogWriter(logger, logLevel, format)

        /**
         * Creates a [TraceFeatureMessageLogWriter] that writes trace events to the given SLF4J [Logger].
         *
         * @param logger The SLF4J logger to write trace events to.
         * @param logLevel The log level to use for trace events (default: INFO).
         * @param format Optional custom formatter for trace events.
         * @return A new [TraceFeatureMessageLogWriter] instance.
         */
        @JavaAPI
        @JvmStatic
        @JvmOverloads
        public fun create(
            logger: Logger,
            logLevel: LogLevel = LogLevel.INFO,
            format: ((FeatureMessage) -> String)? = null,
        ): TraceFeatureMessageLogWriter = TraceFeatureMessageLogWriter(
            targetLogger = logger.toKLogger(),
            logLevel = logLevel,
            format = format,
        )
    }
}
