package ai.koog.agents.features.tracing.writer

import ai.koog.agents.core.feature.message.FeatureMessage
import ai.koog.agents.core.feature.writer.FeatureMessageLogWriter
import io.github.oshai.kotlinlogging.KLogger
import kotlin.jvm.JvmOverloads

/**
 * A concrete implementation of [FeatureMessageLogWriter] that handles logging of feature messages
 * specifically for trace-level operations.
 *
 * This class utilizes a delegate implementation, [TraceFeatureMessageLogWriterImpl],
 * to handle the formatting and processing of messages while maintaining a consistent API.
 *
 * @constructor Creates an instance with the specified logger, log level, and optional message formatter.
 * @param targetLogger The [KLogger] instance used for logging feature messages.
 * @param logLevel The level at which messages will be logged. Defaults to [LogLevel.INFO].
 * @param format An optional lambda used to format [FeatureMessage] instances into strings
 * for logging. If not provided, a default implementation will be used.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public actual class TraceFeatureMessageLogWriter @JvmOverloads actual constructor(
    targetLogger: KLogger,
    logLevel: LogLevel,
    format: ((FeatureMessage) -> String)?
) : FeatureMessageLogWriter(targetLogger, logLevel) {

    private val delegate = TraceFeatureMessageLogWriterImpl(targetLogger, logLevel, format)

    actual override fun FeatureMessage.toLoggerMessage(): String = with(delegate) { toLoggerMessage() }
}
