package ai.koog.agents.features.tracing.writer

import ai.koog.agents.core.feature.message.FeatureMessage
import ai.koog.agents.core.feature.writer.FeatureMessageLogWriter
import io.github.oshai.kotlinlogging.KLogger

/**
 * A message processor that writes trace events to a logger.
 *
 * This writer captures all trace events and writes them to the specified logger at the configured log level.
 * It formats each event type differently to provide clear and readable logs.
 *
 * Tracing to logs is particularly useful for:
 * - Integration with existing logging infrastructure
 * - Real-time monitoring of agent behavior
 * - Filtering and searching trace events using log management tools
 *
 * Example usage:
 * ```kotlin
 * // Create a logger
 * val logger = LoggerFactory.create("ai.koog.agents.tracing")
 *
 * val agent = AIAgent(...) {
 *     install(Tracing) {
 *         // Write trace events to logs at INFO level (default)
 *         addMessageProcessor(TraceFeatureMessageLogWriter(logger))
 *
 *         // Write trace events to logs at DEBUG level
 *         addMessageProcessor(TraceFeatureMessageLogWriter(
 *             targetLogger = logger,
 *             logLevel = LogLevel.DEBUG
 *         ))
 *
 *         // Optionally provide custom formatting
 *         addMessageProcessor(TraceFeatureMessageLogWriter(
 *             targetLogger = logger,
 *             format = { message ->
 *                 "[TRACE] ${message.eventId}: ${message::class.simpleName}"
 *             }
 *         ))
 *     }
 * }
 * ```
 *
 * @param targetLogger The logger to write trace events to
 * @param logLevel The log level to use for trace events (default: INFO)
 * @param format Optional custom formatter for trace events
 */
public class TraceFeatureMessageLogWriter(
    targetLogger: KLogger,
    logLevel: LogLevel = LogLevel.INFO,
    private val format: ((FeatureMessage) -> String)? = null,
) : FeatureMessageLogWriter(targetLogger, logLevel) {

    override fun FeatureMessage.toLoggerMessage(): String {
        if (format != null) {
            return format.invoke(this)
        }

        return this.traceMessage
    }
}
